package com.stratum.feature.forge

import com.stratum.core.domain.sprite.ActorRole
import com.stratum.core.domain.sprite.AnimationState
import com.stratum.core.domain.sprite.FacingLayout
import com.stratum.core.domain.sprite.SpriteSheet

/**
 * Everything the mapper screen can ask for.
 *
 * A sealed list rather than thirty lambdas. The editor has a lot of small verbs
 * by its nature, and threading each one through as its own parameter turns
 * every intermediate composable into a switchboard — which is where the
 * mistakes go: one misrouted callback and the flip button trims the sheet.
 */
sealed interface SpriteMapperAction {

    data class Open(val sheet: SpriteSheet) : SpriteMapperAction
    data object Close : SpriteMapperAction
    data object Undo : SpriteMapperAction

    data class SetGrid(
        val columns: Int? = null,
        val rows: Int? = null,
        val offsetX: Int? = null,
        val offsetY: Int? = null,
        val gutterX: Int? = null,
        val gutterY: Int? = null,
    ) : SpriteMapperAction

    data object TrimToContent : SpriteMapperAction

    data class ToggleFrame(val frameId: String) : SpriteMapperAction
    data class FlipFrame(val frameId: String) : SpriteMapperAction
    data class CyclePivot(val frameId: String) : SpriteMapperAction
    data class DuplicateFrame(val frameId: String) : SpriteMapperAction

    data class SelectState(val state: AnimationState) : SpriteMapperAction
    data class AddToClip(val frameId: String) : SpriteMapperAction
    data class RemoveFromClip(val index: Int) : SpriteMapperAction
    data class MoveInClip(val from: Int, val to: Int) : SpriteMapperAction
    data object ClearClip : SpriteMapperAction
    data class SetFps(val fps: Float) : SpriteMapperAction
    data object ToggleLoops : SpriteMapperAction
    data object FillFallbacks : SpriteMapperAction

    data class SetName(val name: String) : SpriteMapperAction
    data class SetRole(val role: ActorRole) : SpriteMapperAction
    data class SetFacing(val facing: FacingLayout) : SpriteMapperAction

    data object SaveDraft : SpriteMapperAction
    data object Bake : SpriteMapperAction
    data object DismissMessage : SpriteMapperAction
}
