package com.stratum.feature.forge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stratum.core.domain.ai.BasePoseRequest
import com.stratum.core.domain.ai.GeneratedImage
import com.stratum.core.domain.ai.GenerationObserver
import com.stratum.core.domain.ai.ImageReference
import com.stratum.core.domain.ai.PoseFrameRequest
import com.stratum.core.domain.ai.PoseScript
import com.stratum.core.domain.ai.PoseStep
import com.stratum.core.domain.sprite.AnimationState
import com.stratum.core.domain.sprite.PackedSheet
import com.stratum.core.domain.sprite.PoseSheetPlan
import com.stratum.core.domain.sprite.PoseSheetPlanner
import com.stratum.core.domain.sprite.SpriteSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Builds a character one pose at a time.
 *
 * The shape of this screen follows from one fact: a full character is forty
 * calls to an image model, which is several minutes and several dollars. So
 * nothing is held in memory that could be on disk, every pose is written the
 * moment it arrives, and the run can be stopped and resumed at any frame
 * without losing what came before. A pipeline that had to start over on a
 * dropped connection would never finish once.
 */
class PoseForgeViewModel(
    private val drawReference: suspend (BasePoseRequest, GenerationObserver) -> Result<GeneratedImage>,
    private val drawPose: suspend (PoseFrameRequest, GenerationObserver) -> Result<GeneratedImage>,
    private val saveReference: (String, ByteArray) -> Unit,
    private val loadReference: (String) -> ByteArray?,
    /** Cheap enough to ask on every keystroke, unlike reading the file. */
    private val hasReference: (String) -> Boolean,
    private val savePose: (String, String, ByteArray) -> Unit,
    private val dropPose: (String, String) -> Unit,
    private val posesDrawn: (String) -> Set<String>,
    /** Composites the set into a sheet and puts it in the sprite library. */
    private val composeSheet: (String, PoseSheetPlan) -> PackedSheet?,
    private val isProviderConfigured: () -> Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PoseForgeUiState(providerConfigured = isProviderConfigured()),
    )
    val state: StateFlow<PoseForgeUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        val current = _state.value
        _state.value = current.copy(
            providerConfigured = isProviderConfigured(),
            hasReference = current.setId?.let(hasReference) ?: false,
            drawn = current.setId?.let(posesDrawn).orEmpty(),
        )
    }

    fun updateSubject(subject: String) {
        val setId = setIdFor(subject)
        _state.value = _state.value.copy(
            subject = subject,
            setId = setId,
            // Typing a subject that was worked on before finds its poses again,
            // which is what makes coming back to a character cheap. Asked as an
            // existence check rather than a read: this runs on every keystroke,
            // and the reference is a megabyte.
            hasReference = setId?.let(hasReference) ?: false,
            drawn = setId?.let(posesDrawn).orEmpty(),
            savedSheet = null,
        )
    }

    fun updateStyle(style: String) {
        _state.value = _state.value.copy(style = style)
    }

    fun selectScope(scope: PoseScope) {
        _state.value = _state.value.copy(scope = scope)
    }

    fun selectCellSize(pixels: Int) {
        _state.value = _state.value.copy(
            cellSize = pixels.coerceIn(PoseSheetPlanner.MIN_CELL, PoseSheetPlanner.MAX_CELL),
        )
    }

    /**
     * Draws the reference the whole character is edited out of.
     *
     * Deliberately its own step with its own button. It is the one image worth
     * looking at before spending forty more on it — every later frame inherits
     * this character's face, palette and armour, so a reference that came back
     * wrong is worth another attempt before the expensive part begins.
     */
    fun drawReferencePose() {
        val current = _state.value
        val setId = current.setId
        if (setId == null || current.subject.isBlank()) {
            _state.value = current.copy(error = "Describe the character first.")
            return
        }
        if (!isProviderConfigured()) {
            _state.value = current.copy(error = "Add a provider API key in settings first.")
            return
        }

        _state.value = current.copy(busy = true, error = null, message = null, savedSheet = null)
        job = viewModelScope.launch {
            val result = drawReference(
                BasePoseRequest(subject = current.subject.trim(), styleDirection = current.style),
                GenerationObserver.None,
            )
            _state.value = result.fold(
                onSuccess = { image ->
                    saveReference(setId, image.bytes)
                    _state.value.copy(
                        busy = false,
                        hasReference = true,
                        message = "Reference drawn. Check it before building the animations — " +
                            "every frame inherits this character.",
                    )
                },
                onFailure = { cause ->
                    _state.value.copy(busy = false, error = cause.message ?: "The reference failed.")
                },
            )
        }
    }

    /**
     * Works through the script, one pose at a time.
     *
     * Sequential rather than parallel, and not only to be polite to a rate
     * limit. A person watching forty frames appear wants to stop when the third
     * one comes back wrong, and eight in flight at once means eight more
     * arriving after they have already decided to stop.
     */
    fun buildAnimations() {
        val current = _state.value
        val setId = current.setId
        if (setId == null) {
            _state.value = current.copy(error = "Describe the character first.")
            return
        }
        val referenceBytes = loadReference(setId)
        if (referenceBytes == null) {
            _state.value = current.copy(error = "Draw the reference pose first.")
            return
        }
        if (!isProviderConfigured()) {
            _state.value = current.copy(error = "Add a provider API key in settings first.")
            return
        }

        val reference = ImageReference(referenceBytes)
        val script = current.script
        val todo = script.remaining(posesDrawn(setId))
        if (todo.isEmpty()) {
            _state.value = current.copy(message = "Every pose is already drawn. Build the sheet.")
            return
        }

        _state.value = current.copy(
            busy = true,
            error = null,
            message = null,
            failures = emptyMap(),
            savedSheet = null,
        )
        job = viewModelScope.launch {
            var failures = emptyMap<String, String>()
            for (step in todo) {
                _state.value = _state.value.copy(currentStep = step)
                val result = runCatching {
                    drawPose(
                        PoseFrameRequest(
                            reference = reference,
                            step = step,
                            styleDirection = _state.value.style,
                        ),
                        GenerationObserver.None,
                    )
                }.getOrElse { cause ->
                    if (cause is CancellationException) throw cause
                    Result.failure(cause)
                }

                result.fold(
                    onSuccess = { image ->
                        savePose(setId, step.key, image.bytes)
                        _state.value = _state.value.copy(drawn = posesDrawn(setId))
                    },
                    onFailure = { cause ->
                        // One bad frame does not end the run. Thirty-nine good
                        // poses and a list of which to retry is a far better
                        // place to be than nothing.
                        failures = failures + (step.key to (cause.message ?: "failed"))
                        _state.value = _state.value.copy(failures = failures)
                    },
                )
            }
            _state.value = _state.value.copy(
                busy = false,
                currentStep = null,
                message = if (failures.isEmpty()) {
                    "Every pose drawn. Build the sheet."
                } else {
                    "${failures.size} pose${if (failures.size == 1) "" else "s"} failed. " +
                        "Run it again to retry just those."
                },
            )
        }
    }

    /** Throws one pose away so the next run draws it again. */
    fun redrawPose(key: String) {
        val setId = _state.value.setId ?: return
        dropPose(setId, key)
        _state.value = _state.value.copy(
            drawn = posesDrawn(setId),
            failures = _state.value.failures - key,
            savedSheet = null,
        )
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(
            busy = false,
            currentStep = null,
            message = "Stopped. The poses already drawn are kept.",
        )
    }

    /**
     * Packs what has been drawn into a sheet.
     *
     * Allowed before the set is complete on purpose. A character with an idle
     * and a walk is playable, and being able to see it in the world after eight
     * generations rather than forty is the difference between a pipeline
     * someone uses and one they read about.
     */
    fun buildSheet() {
        val current = _state.value
        val setId = current.setId ?: return
        val drawn = posesDrawn(setId)
        if (drawn.isEmpty()) {
            _state.value = current.copy(error = "No poses have been drawn yet.")
            return
        }

        // Only the states that actually have frames, and only as many as
        // arrived: a row planned for four and given two would leave two cells
        // of nothing in the middle of the animation.
        val counts = current.script.states.associateWith { state ->
            current.script.stepsFor(state).count { it.key in drawn }
        }.filterValues { it > 0 }

        val plan = PoseSheetPlanner.plan(
            id = setId,
            name = current.subject.trim().ifBlank { "Character" },
            frameCounts = counts,
            cellSize = current.cellSize,
        )
        if (plan == null) {
            _state.value = current.copy(error = "There is nothing to pack yet.")
            return
        }

        _state.value = current.copy(busy = true, error = null)
        job = viewModelScope.launch {
            // Decoding, keying and downscaling forty 1024-pixel images is
            // seconds of work. On the main thread that is not a slow screen, it
            // is a frozen one.
            val packed = withContext(Dispatchers.Default) { composeSheet(setId, plan) }
            _state.value = if (packed == null) {
                _state.value.copy(busy = false, error = "The sheet could not be written.")
            } else {
                val sheet = packed.sheet
                _state.value.copy(
                    busy = false,
                    savedSheet = sheet,
                    message = buildString {
                        append("Saved as a ${sheet.columns}x${sheet.rows} sheet at ")
                        append("${current.cellSize}px a frame. ")
                        // A hole in an animation looks exactly like a frame the
                        // character is invisible for, so it is named rather
                        // than left to be noticed in the world.
                        if (packed.missing.isNotEmpty()) {
                            append("${packed.missing.size} pose(s) could not be read and left ")
                            append("empty cells: ${packed.missing.joinToString()}. ")
                        }
                        append("Open it in the frame mapper to adjust it.")
                    },
                )
            }
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    /**
     * A character's poses live under an id derived from what it is, so typing
     * the same description again finds the same set rather than paying for it
     * twice.
     */
    private fun setIdFor(subject: String): String? {
        val slug = subject.lowercase().replace(NON_ID, "_").trim('_').take(MAX_SLUG)
        return if (slug.isBlank()) null else "pose:$slug"
    }

    companion object {
        private val NON_ID = Regex("[^a-z0-9]+")
        private const val MAX_SLUG = 32

        /** Frame sizes worth packing down to, at this camera. */
        val CELL_SIZES = listOf(64, 96, 128, 192)

        fun factory(
            drawReference: suspend (BasePoseRequest, GenerationObserver) -> Result<GeneratedImage>,
            drawPose: suspend (PoseFrameRequest, GenerationObserver) -> Result<GeneratedImage>,
            saveReference: (String, ByteArray) -> Unit,
            loadReference: (String) -> ByteArray?,
            hasReference: (String) -> Boolean,
            savePose: (String, String, ByteArray) -> Unit,
            dropPose: (String, String) -> Unit,
            posesDrawn: (String) -> Set<String>,
            composeSheet: (String, PoseSheetPlan) -> PackedSheet?,
            isProviderConfigured: () -> Boolean,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PoseForgeViewModel(
                drawReference, drawPose, saveReference, loadReference, hasReference, savePose,
                dropPose, posesDrawn, composeSheet, isProviderConfigured,
            ) as T
        }
    }
}

/** How much of a character to draw. Every state is more calls and more money. */
enum class PoseScope(val label: String, val script: PoseScript) {
    /** Idle, walk, attack, death: what an enemy is actually seen doing. */
    ENEMY("Enemy", PoseScript.enemy()),

    /** Everything, for the character a player looks at all session. */
    FULL("Full character", PoseScript.full()),
}

data class PoseForgeUiState(
    val subject: String = "",
    val style: String = "",
    val scope: PoseScope = PoseScope.ENEMY,
    val cellSize: Int = PoseSheetPlanner.DEFAULT_CELL,
    val setId: String? = null,
    val hasReference: Boolean = false,
    /** Pose keys already on disk. */
    val drawn: Set<String> = emptySet(),
    val busy: Boolean = false,
    val currentStep: PoseStep? = null,
    val failures: Map<String, String> = emptyMap(),
    val savedSheet: SpriteSheet? = null,
    val providerConfigured: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val script: PoseScript get() = scope.script

    val total: Int get() = script.steps.size

    val completed: Int get() = script.steps.count { it.key in drawn }

    val progress: Float get() = script.progress(drawn)

    val canBuildAnimations: Boolean get() = hasReference && !busy && providerConfigured

    val canBuildSheet: Boolean get() = completed > 0 && !busy

    /** What is being drawn right now, in the words the model was given. */
    val currentLabel: String?
        get() = currentStep?.let { "${it.state.name.lowercase()} ${it.index + 1}" }

    fun statesWithFrames(): List<AnimationState> =
        script.states.filter { state -> script.stepsFor(state).any { it.key in drawn } }
}
