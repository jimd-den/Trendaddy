package com.stratum.feature.forge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stratum.core.domain.ai.GenerateSpriteSheetUseCase
import com.stratum.core.domain.ai.SheetLayout
import com.stratum.core.domain.ai.SpriteSheetRequest
import com.stratum.core.domain.ai.GenerationAttempt
import com.stratum.core.domain.ai.GenerationJournal
import com.stratum.core.domain.ai.GenerationObserver
import com.stratum.core.domain.ai.GenerationStage
import com.stratum.core.domain.sprite.SpriteSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives sprite sheet generation.
 *
 * Generated sheets are saved immediately and kept, however they turned out. A
 * mediocre sheet is still better than a coloured disc, and letting the player
 * regenerate is cheaper than making them judge one before it is stored.
 */
class SpriteForgeViewModel(
    private val generateSheet: GenerateSpriteSheetUseCase,
    private val saveSheet: (SpriteSheet, ByteArray) -> Unit,
    private val loadSheets: () -> List<SpriteSheet>,
    private val deleteSheet: (String) -> Unit,
    private val isProviderConfigured: () -> Boolean,
    /** The last few provider calls, so a failure can be read rather than guessed at. */
    private val journal: GenerationJournal = GenerationJournal(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        SpriteForgeUiState(
            sheets = loadSheets(),
            providerConfigured = isProviderConfigured(),
        ),
    )
    val state: StateFlow<SpriteForgeUiState> = _state.asStateFlow()

    fun updateSubject(subject: String) {
        _state.value = _state.value.copy(subject = subject)
    }

    fun updateStyle(style: String) {
        _state.value = _state.value.copy(style = style)
    }

    fun selectTarget(target: SpriteTarget) {
        _state.value = _state.value.copy(target = target)
    }

    fun refresh() {
        _state.value = _state.value.copy(
            sheets = loadSheets(),
            providerConfigured = isProviderConfigured(),
        )
    }

    fun generate() {
        val current = _state.value
        val subject = current.subject.trim()
        if (subject.isEmpty()) {
            _state.value = current.copy(error = "Describe what to draw first.")
            return
        }
        if (!isProviderConfigured()) {
            _state.value = current.copy(error = "Add a provider API key in settings first.")
            return
        }

        _state.value = current.copy(
            busy = true,
            error = null,
            lastGenerated = null,
            stage = GenerationStage.PREPARING,
            attempt = null,
        )

        // Reports what the adapter is doing and exactly what it sent. An image
        // model can sit on a request for minutes; a spinner that says nothing
        // for minutes is indistinguishable from a hang, and a rejection with no
        // request to look at is indistinguishable from a bug.
        val observer = object : GenerationObserver {
            override fun onStage(stage: GenerationStage) {
                _state.value = _state.value.copy(stage = stage)
            }

            override fun onAttempt(attempt: GenerationAttempt) {
                journal.record(attempt)
                _state.value = _state.value.copy(attempt = attempt)
            }
        }

        viewModelScope.launch {
            val result = generateSheet(
                SpriteSheetRequest(
                    subject = subject,
                    namespace = current.target.namespace,
                    styleDirection = current.style,
                    layout = current.target.layout,
                ),
                observer,
            )
            _state.value = result.fold(
                onSuccess = { generated ->
                    _state.value = _state.value.copy(stage = GenerationStage.SAVING)
                    saveSheet(generated.sheet, generated.image.bytes)
                    _state.value.copy(
                        busy = false,
                        stage = GenerationStage.DONE,
                        sheets = loadSheets(),
                        lastGenerated = generated.sheet,
                        error = null,
                    )
                },
                onFailure = { cause ->
                    _state.value.copy(
                        busy = false,
                        stage = GenerationStage.FAILED,
                        error = cause.message ?: "Generation failed.",
                    )
                },
            )
        }
    }

    /** Opens or closes the panel showing exactly what was sent and what came back. */
    fun toggleDetails() {
        _state.value = _state.value.copy(detailsOpen = !_state.value.detailsOpen)
    }

    fun delete(sheetId: String) {
        deleteSheet(sheetId)
        _state.value = _state.value.copy(
            sheets = loadSheets(),
            lastGenerated = _state.value.lastGenerated?.takeIf { it.id != sheetId },
        )
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    companion object {
        fun factory(
            generateSheet: GenerateSpriteSheetUseCase,
            saveSheet: (SpriteSheet, ByteArray) -> Unit,
            loadSheets: () -> List<SpriteSheet>,
            deleteSheet: (String) -> Unit,
            isProviderConfigured: () -> Boolean,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SpriteForgeViewModel(
                generateSheet, saveSheet, loadSheets, deleteSheet, isProviderConfigured,
            ) as T
        }
    }
}

/**
 * What the sheet is for. Decides how many rows to ask for: a monster that only
 * walks does not need an attack row, and asking a weak image model for fewer
 * frames is the single most effective way to get usable art out of it.
 */
enum class SpriteTarget(val label: String, val namespace: String, val layout: SheetLayout) {
    HERO("Hero", "hero", SheetLayout.standard()),
    MONSTER("Monster", "monster", SheetLayout.simple()),
}

data class SpriteForgeUiState(
    val subject: String = "",
    val style: String = "",
    val target: SpriteTarget = SpriteTarget.HERO,
    val busy: Boolean = false,
    val sheets: List<SpriteSheet> = emptyList(),
    val lastGenerated: SpriteSheet? = null,
    val error: String? = null,
    val providerConfigured: Boolean = false,
    val stage: GenerationStage? = null,
    /** The provider call behind the current result, successful or not. */
    val attempt: GenerationAttempt? = null,
    val detailsOpen: Boolean = false,
) {
    val progress: Float get() = stage?.fraction ?: 0f

    val stageLabel: String? get() = stage?.label
}
