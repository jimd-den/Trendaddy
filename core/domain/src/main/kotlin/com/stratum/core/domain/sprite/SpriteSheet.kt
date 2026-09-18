package com.stratum.core.domain.sprite

/**
 * What an actor is doing, for animation purposes.
 *
 * A fixed set rather than pack data: the engine drives these from simulation
 * state, so a pack inventing a seventh state would have nothing to trigger it.
 * A pack that lacks frames for one of these falls back to [IDLE].
 */
enum class AnimationState {
    IDLE,
    WALK,
    ATTACK,

    /**
     * A skill, as opposed to a basic swing. Separate because a class's power
     * should not look like its ordinary attack — if the two read the same, the
     * resource you spent bought nothing you can see.
     */
    SPECIAL,
    HURT,
    ROLL,
    DIE;

    companion object {
        /** States that play once and stop rather than looping. */
        val oneShot = setOf(ATTACK, SPECIAL, HURT, DIE)
    }
}

/** Which way an actor is drawn. Four is enough for a 2:1 isometric view. */
enum class SpriteFacing {
    SOUTH_EAST,
    SOUTH_WEST,
    NORTH_WEST,
    NORTH_EAST;

    /**
     * Whether this facing should be drawn flipped.
     *
     * A generated sheet reliably contains one facing, not four. Mirroring buys
     * the other side for nothing, and at a three-quarter camera a mirrored
     * character reads correctly — which is cheaper and more dependable than
     * asking a model for four consistent angles of the same figure.
     */
    val mirrored: Boolean get() = this == SOUTH_WEST || this == NORTH_WEST

    companion object {
        /**
         * Maps a world direction onto the four drawn angles. North and west both
         * read as "away", which is why four sprites cover eight directions
         * convincingly at this camera angle.
         */
        fun of(dx: Int, dy: Int): SpriteFacing = when {
            dx > 0 && dy >= 0 -> SOUTH_EAST
            dx <= 0 && dy > 0 -> SOUTH_WEST
            dx < 0 && dy <= 0 -> NORTH_WEST
            else -> NORTH_EAST
        }
    }
}

/**
 * One animation: a run of frames at a fixed rate.
 *
 * Frames are indices into the sheet's grid, read left to right and top to
 * bottom, so a pack describes a row rather than listing coordinates.
 */
data class AnimationClip(
    val state: AnimationState,
    val firstFrame: Int,
    val frameCount: Int,
    val frameDurationMs: Int = 120,
    val loops: Boolean = true,
) {
    init {
        require(frameCount > 0) { "Clip for $state has no frames" }
        require(frameDurationMs > 0) { "Clip for $state has a zero frame duration" }
    }

    val durationMs: Int get() = frameCount * frameDurationMs

    /**
     * Which frame to show after [elapsedMs].
     *
     * A one-shot clip holds its last frame rather than snapping back, so an
     * attack ends on the follow-through instead of the wind-up.
     */
    fun frameAt(elapsedMs: Long): Int {
        if (frameCount == 1) return firstFrame
        val step = if (loops) {
            ((elapsedMs / frameDurationMs) % frameCount).toInt()
        } else {
            ((elapsedMs / frameDurationMs).toInt()).coerceAtMost(frameCount - 1)
        }
        return firstFrame + step
    }

    fun isFinished(elapsedMs: Long): Boolean = !loops && elapsedMs >= durationMs
}

/**
 * A sheet of frames plus the clips that index into it.
 *
 * Holds no image data: the bytes live wherever the platform keeps them, keyed
 * by [id]. That is what lets this stay in the pure domain and be generated,
 * validated and tested without a bitmap.
 */
data class SpriteSheet(
    val id: String,
    val name: String,
    val columns: Int,
    val rows: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val clips: List<AnimationClip> = emptyList(),
    /** Present when the sheet has a row per facing; null means one row serves all. */
    val facingRows: Map<SpriteFacing, Int> = emptyMap(),
    val origin: SpriteOrigin = SpriteOrigin.PACK,
) {
    val frameCount: Int get() = columns * rows

    fun clip(state: AnimationState): AnimationClip? = clips.firstOrNull { it.state == state }

    /** The clip for a state, falling back to idle, falling back to anything. */
    fun clipOrFallback(state: AnimationState): AnimationClip? =
        clip(state) ?: clip(AnimationState.IDLE) ?: clips.firstOrNull()

    /** Grid position of a frame index, for the renderer to cut. */
    fun frameRect(frame: Int): FrameRect {
        val safe = frame.coerceIn(0, (frameCount - 1).coerceAtLeast(0))
        return FrameRect(
            left = (safe % columns) * frameWidth,
            top = (safe / columns) * frameHeight,
            width = frameWidth,
            height = frameHeight,
        )
    }

    /**
     * Adds the row offset for a facing, when the sheet has one. Sheets without
     * per-facing rows return the frame unchanged, so a one-row sheet still
     * animates -- it just faces the same way whichever direction you walk.
     */
    fun frameFor(frame: Int, facing: SpriteFacing): Int {
        val row = facingRows[facing] ?: return frame
        return frame + row * columns
    }

    /** A sheet whose grid does not match its declared frames cannot be cut. */
    val isCoherent: Boolean
        get() = columns > 0 && rows > 0 && frameWidth > 0 && frameHeight > 0 &&
            clips.all { it.firstFrame + it.frameCount <= frameCount }
}

enum class SpriteOrigin { PACK, AI_GENERATED, IMPORTED }

data class FrameRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Where an actor is in its animation.
 *
 * Immutable and advanced by the caller, so the renderer never owns timing and
 * two actors in the same state stay independent.
 */
data class AnimationPlayback(
    val state: AnimationState = AnimationState.IDLE,
    val elapsedMs: Long = 0L,
) {
    fun advanced(deltaMs: Long): AnimationPlayback = copy(elapsedMs = elapsedMs + deltaMs)

    /**
     * Switches state, restarting the clock only when the state actually changes.
     * Restarting on every call would freeze a looping walk on its first frame.
     */
    fun transitionTo(next: AnimationState): AnimationPlayback =
        if (next == state) this else AnimationPlayback(next, 0L)

    fun frameIn(sheet: SpriteSheet): Int {
        val clip = sheet.clipOrFallback(state) ?: return 0
        return clip.frameAt(elapsedMs)
    }

    fun isFinishedIn(sheet: SpriteSheet): Boolean {
        val clip = sheet.clip(state) ?: return true
        return clip.isFinished(elapsedMs)
    }
}

/**
 * Chooses the animation state from what an actor is actually doing.
 *
 * One place, shared by the player and by monsters, so a monster being hit and a
 * player being hit never drift apart.
 */
object AnimationSelector {

    fun select(
        isDead: Boolean,
        isRolling: Boolean = false,
        wasHitRecently: Boolean = false,
        isAttacking: Boolean = false,
        isCasting: Boolean = false,
        isMoving: Boolean = false,
    ): AnimationState = when {
        isDead -> AnimationState.DIE
        isRolling -> AnimationState.ROLL
        // Being hit interrupts an attack: the flinch is the more urgent
        // information, because it tells the player they are losing the trade.
        wasHitRecently -> AnimationState.HURT
        // A skill outranks a swing: it costs something, so it should be the
        // thing you see when both are in flight.
        isCasting -> AnimationState.SPECIAL
        isAttacking -> AnimationState.ATTACK
        isMoving -> AnimationState.WALK
        else -> AnimationState.IDLE
    }
}
