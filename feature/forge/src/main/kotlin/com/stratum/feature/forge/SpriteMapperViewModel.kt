package com.stratum.feature.forge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stratum.core.domain.sprite.ActorRole
import com.stratum.core.domain.sprite.AnimationFallback
import com.stratum.core.domain.sprite.AnimationState
import com.stratum.core.domain.sprite.AtlasBaker
import com.stratum.core.domain.sprite.FacingLayout
import com.stratum.core.domain.sprite.FramePivot
import com.stratum.core.domain.sprite.SheetGrid
import com.stratum.core.domain.sprite.SliceSpec
import com.stratum.core.domain.sprite.SpriteAtlas
import com.stratum.core.domain.sprite.SpriteMapper
import com.stratum.core.domain.sprite.SpriteSheet
import com.stratum.core.domain.sprite.SpriteSlicing
import com.stratum.core.domain.sprite.SpriteValidation
import com.stratum.core.domain.sprite.SpriteValidationReport
import com.stratum.core.domain.sprite.ValidationSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the frame mapper.
 *
 * Every edit is the same shape: take the atlas, ask the domain for a new one,
 * push the old one onto the undo stack. Nothing here decides anything about
 * sprites — the grid arithmetic, the fallback chains, the packing and the
 * validation all live in `:core:domain` and are tested without a device. This
 * is the part that remembers which state's tab is open.
 */
class SpriteMapperViewModel(
    /** A project already saved under this id, if there is one. */
    private val openProject: (String) -> SpriteAtlas?,
    /** The source image's pixels, for trimming frames to their content. */
    private val sourcePixels: (SpriteAtlas) -> IntArray?,
    /** Copies a stored sheet's art into a new project and hands back its size. */
    private val startProject: (SpriteSheet) -> SpriteAtlas?,
    private val saveProject: (SpriteAtlas) -> Unit,
    /** Packs the mapping into a sheet the engine can draw, and stores it. */
    private val bakeAtlas: (SpriteAtlas) -> SpriteSheet?,
    private val loadSheets: () -> List<SpriteSheet>,
) : ViewModel() {

    private val _state = MutableStateFlow(SpriteMapperUiState(sheets = loadSheets()))
    val state: StateFlow<SpriteMapperUiState> = _state.asStateFlow()

    private val undo = ArrayDeque<SpriteAtlas>()

    fun refresh() {
        _state.value = _state.value.copy(sheets = loadSheets())
    }

    /**
     * Opens a stored sheet for mapping, resuming the project if one exists.
     *
     * Resuming matters more than it looks. A person who maps twenty frames,
     * leaves to check something and comes back to an empty grid does not map
     * twenty frames a second time; they stop using the editor.
     */
    fun open(sheet: SpriteSheet) {
        val existing = openProject(sheet.id)
        val atlas = existing ?: startProject(sheet)
        if (atlas == null) {
            _state.value = _state.value.copy(
                error = "That sheet's image could not be read, so there is nothing to map.",
            )
            return
        }
        undo.clear()
        _state.value = _state.value.copy(
            atlas = atlas,
            slice = sliceOf(atlas, SheetGrid(sheet.columns, sheet.rows)),
            activeState = atlas.mappedStates.firstOrNull() ?: AnimationState.IDLE,
            report = SpriteValidation.validate(atlas),
            savedSheet = null,
            message = if (existing != null) "Picked up where you left off." else null,
            error = null,
        )
    }

    fun close() {
        undo.clear()
        _state.value = _state.value.copy(atlas = null, slice = null, savedSheet = null, message = null)
    }

    // ---- the grid --------------------------------------------------------

    fun setGrid(
        columns: Int = _state.value.slice?.columns ?: 1,
        rows: Int = _state.value.slice?.rows ?: 1,
        offsetX: Int = _state.value.slice?.offsetX ?: 0,
        offsetY: Int = _state.value.slice?.offsetY ?: 0,
        gutterX: Int = _state.value.slice?.gutterX ?: 0,
        gutterY: Int = _state.value.slice?.gutterY ?: 0,
    ) {
        val atlas = _state.value.atlas ?: return
        val spec = SliceSpec.fitting(
            grid = SheetGrid(columns.coerceIn(1, MAX_DIVISIONS), rows.coerceIn(1, MAX_DIVISIONS)),
            imageWidth = atlas.sourceWidth,
            imageHeight = atlas.sourceHeight,
            offsetX = offsetX.coerceAtLeast(0),
            offsetY = offsetY.coerceAtLeast(0),
            gutterX = gutterX.coerceAtLeast(0),
            gutterY = gutterY.coerceAtLeast(0),
        ) ?: run {
            // The numbers no longer describe a grid that fits on the image.
            // Refusing is better than silently snapping back to something the
            // person did not ask for and cannot see they did not get.
            _state.value = _state.value.copy(
                error = "That grid does not fit on the image. Reduce the margin, the gap, or " +
                    "the number of cells.",
            )
            return
        }
        edit(clearsBake = true) { SpriteSlicing.reslice(it, spec) }
        _state.value = _state.value.copy(slice = spec, error = null)
    }

    // ---- frames ----------------------------------------------------------

    fun toggleFrame(frameId: String) = edit { it.toggleFrame(frameId) }

    fun flipFrame(frameId: String) = edit { it.flipFrame(frameId) }

    fun duplicateFrame(frameId: String) = edit { it.duplicateFrame(frameId) }

    fun removeFrame(frameId: String) = edit { it.removeFrame(frameId) }

    /** Steps a frame's anchor round the three that matter, rather than opening a menu. */
    fun cyclePivot(frameId: String) = edit { atlas ->
        val current = atlas.frame(frameId)?.pivot ?: FramePivot.BOTTOM_CENTER
        val next = FramePivot.entries[(current.ordinal + 1) % FramePivot.entries.size]
        atlas.setPivot(frameId, next)
    }

    /**
     * Shrinks every frame to what is drawn in it, and switches the blank ones
     * off.
     *
     * The single most useful button on the screen, which is why it is one
     * button. It does the two jobs a person would otherwise do by hand sixteen
     * times: finding the empty cells, and making the pivot mean something.
     */
    fun trimToContent() {
        val atlas = _state.value.atlas ?: return
        val pixels = sourcePixels(atlas)
        if (pixels == null) {
            _state.value = _state.value.copy(error = "The source image could not be read.")
            return
        }
        edit(clearsBake = true) { SpriteSlicing.trimToContent(it, pixels) }
        _state.value = _state.value.copy(
            message = "Frames trimmed to their content and blank cells switched off.",
            error = null,
        )
    }

    // ---- clips -----------------------------------------------------------

    fun selectState(state: AnimationState) {
        _state.value = _state.value.copy(activeState = state)
    }

    /** Adds a frame to the state whose tab is open, which is the whole interaction. */
    fun addToActiveClip(frameId: String) =
        edit { it.appendToClip(_state.value.activeState, frameId) }

    fun removeFromActiveClip(index: Int) =
        edit { it.removeFromClip(_state.value.activeState, index) }

    fun moveInActiveClip(from: Int, to: Int) =
        edit { it.moveInClip(_state.value.activeState, from, to) }

    fun clearActiveClip() = edit { it.clearClip(_state.value.activeState) }

    fun setActiveFps(fps: Float) = edit(record = false) { atlas ->
        val safe = fps.coerceIn(MIN_FPS, MAX_FPS)
        atlas.setClipTiming(
            state = _state.value.activeState,
            frameDurationMs = (1000f / safe).toInt(),
        )
    }

    fun toggleActiveLoops() = edit { atlas ->
        val state = _state.value.activeState
        atlas.setClipTiming(state, loops = !(atlas.clip(state)?.loops ?: true))
    }

    /** Fills every unmapped state from the ones that do have frames. */
    fun fillFallbacks() {
        edit { AnimationFallback.fill(it) }
        _state.value = _state.value.copy(
            message = "Unmapped states filled from the nearest art. Replace them when you have it.",
        )
    }

    // ---- the asset -------------------------------------------------------

    fun setName(name: String) = edit(record = false) { it.copy(name = name) }

    fun setRole(role: ActorRole) = edit { it.copy(role = role) }

    fun setFacing(facing: FacingLayout) = edit { it.copy(facing = facing) }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        _state.value = _state.value.copy(
            atlas = previous,
            report = SpriteValidation.validate(previous),
            savedSheet = null,
        )
    }

    /** Stores the mapping without baking, so the work survives leaving the screen. */
    fun saveDraft() {
        val atlas = _state.value.atlas ?: return
        saveProject(atlas)
        _state.value = _state.value.copy(message = "Draft saved.", error = null)
    }

    /**
     * Packs the mapping into a sheet and puts it in the library.
     *
     * Blocked atlases are refused here rather than at the point of binding,
     * because this is the screen where the missing frames can actually be
     * supplied. A sheet that silently draws nothing in the world is the one
     * failure nobody can diagnose from where it appears.
     */
    fun bake() {
        val atlas = _state.value.atlas ?: return
        val report = SpriteValidation.validate(atlas)
        if (report.isBlocked) {
            _state.value = _state.value.copy(
                report = report,
                error = report.messages.firstOrNull { it.severity == ValidationSeverity.BLOCKED }?.text
                    ?: "This mapping is not playable yet.",
            )
            return
        }

        _state.value = _state.value.copy(busy = true, error = null)
        saveProject(atlas)
        val sheet = bakeAtlas(atlas.pruned())
        _state.value = if (sheet == null) {
            _state.value.copy(busy = false, error = "The sheet could not be written.")
        } else {
            _state.value.copy(
                busy = false,
                savedSheet = sheet,
                sheets = loadSheets(),
                report = report,
                message = "Saved as a ${sheet.columns}x${sheet.rows} sheet, " +
                    "${sheet.clips.size} animation${if (sheet.clips.size == 1) "" else "s"}.",
            )
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    /**
     * The one path every edit takes.
     *
     * [record] is false for the continuous ones — dragging an FPS slider, typing
     * a name — because an undo stack that replays a slider one pixel at a time
     * is an undo stack nobody can reach past.
     */
    private fun edit(
        record: Boolean = true,
        clearsBake: Boolean = false,
        change: (SpriteAtlas) -> SpriteAtlas,
    ) {
        val current = _state.value.atlas ?: return
        val next = change(current)
        if (next == current) return
        if (record) {
            undo.addLast(current)
            while (undo.size > UNDO_DEPTH) undo.removeFirst()
        }
        _state.value = _state.value.copy(
            atlas = next,
            report = SpriteValidation.validate(next),
            savedSheet = if (clearsBake) null else _state.value.savedSheet,
        )
    }

    private fun sliceOf(atlas: SpriteAtlas, grid: SheetGrid): SliceSpec? =
        SliceSpec.fitting(grid, atlas.sourceWidth, atlas.sourceHeight)

    companion object {
        /** More divisions than this on a phone screen is a grid nobody can tap. */
        const val MAX_DIVISIONS = 16
        const val MIN_FPS = 1f
        const val MAX_FPS = 30f
        private const val UNDO_DEPTH = 40

        fun factory(
            openProject: (String) -> SpriteAtlas?,
            sourcePixels: (SpriteAtlas) -> IntArray?,
            startProject: (SpriteSheet) -> SpriteAtlas?,
            saveProject: (SpriteAtlas) -> Unit,
            bakeAtlas: (SpriteAtlas) -> SpriteSheet?,
            loadSheets: () -> List<SpriteSheet>,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SpriteMapperViewModel(
                openProject, sourcePixels, startProject, saveProject, bakeAtlas, loadSheets,
            ) as T
        }
    }
}

data class SpriteMapperUiState(
    /** Null until a sheet is opened: the screen is a picker first and an editor second. */
    val atlas: SpriteAtlas? = null,
    val sheets: List<SpriteSheet> = emptyList(),
    val slice: SliceSpec? = null,
    val activeState: AnimationState = AnimationState.IDLE,
    val report: SpriteValidationReport? = null,
    val busy: Boolean = false,
    val savedSheet: SpriteSheet? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val isEditing: Boolean get() = atlas != null

    /** The frames of the open state, in play order, for the strip and the preview. */
    val activeFrames get() = atlas?.framesOf(activeState).orEmpty()

    val activeClip get() = atlas?.clip(activeState)

    val canBake: Boolean get() = atlas != null && !busy && report?.isBlocked != true

    /** How many cells are switched on, which is the number a person is watching. */
    val usableFrames: Int get() = atlas?.enabledFrames?.size ?: 0

    val plannedSheet get() = atlas?.let { AtlasBaker.plan(it) }
}
