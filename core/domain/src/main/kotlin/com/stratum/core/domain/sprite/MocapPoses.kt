package com.stratum.core.domain.sprite

/**
 * Every pose the pipeline knows, as joint angles.
 *
 * Not motion capture in the literal sense — nobody wore a suit — but the same
 * job: a skeleton per frame, authored once, that everything else reads from.
 * Three things need it and all three were previously guessing.
 *
 * The image model needs it, because prose is a poor way to specify a body.
 * "Left leg forward with the heel touching the ground, right arm swung forward"
 * is unambiguous to a person and merely suggestive to an image model, which is
 * why generated attack frames kept coming back as a cross-body guard instead of
 * an impact. A drawing of the pose is not suggestive.
 *
 * The weapon rig needs it, because a weapon is held in a hand and points along
 * a forearm — and a skeleton knows exactly where both are. Anchors used to be
 * authored blind and then corrected per character; measured against real art,
 * the blind version put the hand a full hand's width outside the body.
 *
 * And the renderer needs it for the same reason, so the number tuned against
 * the guide is the number the game draws with.
 *
 * Angles are degrees anticlockwise from straight down. An arm at the side is 0,
 * out to the near side is 90, overhead is 180. Elbows and knees are relative to
 * the limb above them, so bending an elbow does not move the shoulder.
 */
object MocapPoses {

    /** The frames of one animation, in play order. */
    fun framesFor(state: AnimationState): List<PoseAngles> = when (state) {
        AnimationState.IDLE -> idle
        AnimationState.WALK -> walk
        AnimationState.ATTACK -> attack
        AnimationState.SPECIAL -> special
        AnimationState.HURT -> hurt
        AnimationState.ROLL -> roll
        AnimationState.DIE -> die
    }

    /**
     * The pose for one frame, tolerant of a clip that came back a different
     * length than the script asked for.
     *
     * A model that returned five attack frames instead of four should still get
     * a rig, and stretching the authored frames across whatever arrived is a
     * better answer than refusing or than leaving the last frame unposed.
     */
    fun poseFor(state: AnimationState, index: Int, frameCount: Int): PoseAngles {
        val frames = framesFor(state)
        if (frames.isEmpty()) return PoseAngles()
        if (frameCount <= 1) return frames.first()
        val t = (index.toFloat() / (frameCount - 1)).coerceIn(0f, 1f)
        val at = (t * (frames.size - 1)).toInt().coerceIn(0, frames.size - 1)
        return frames[at]
    }

    /** Standing, and the same standing a fraction taller on the in-breath. */
    private val idle = listOf(
        PoseAngles(),
        PoseAngles(driftY = -0.008f, lean = 1f, shoulderNear = 9f, shoulderFar = -9f),
    )

    /**
     * Contact, passing, contact, passing.
     *
     * The oldest four frames in animation. Arms swing opposite the legs, and
     * the body drops at the contacts and rises through the passes — which is
     * the part that makes it read as walking rather than as gliding.
     */
    private val walk = listOf(
        // Contact: front heel down with the leg nearly straight, back leg
        // trailing with only a little bend so the toe stays down.
        PoseAngles(
            hipNear = 25f, kneeNear = -6f, hipFar = -22f, kneeFar = 10f,
            shoulderNear = -24f, elbowNear = 14f, shoulderFar = 24f, elbowFar = -14f,
            driftY = 0.014f,
        ),
        // Passing: the swing leg comes through *under* the body with the shin
        // folded back, which is a negative knee. Drawn out, bending it forward
        // instead threw the foot out ahead of the figure and read as a kick.
        PoseAngles(
            hipNear = 4f, kneeNear = 0f, hipFar = 20f, kneeFar = -55f,
            shoulderNear = -6f, elbowNear = 8f, shoulderFar = 6f, elbowFar = -8f,
            driftY = -0.004f,
        ),
        PoseAngles(
            hipNear = -22f, kneeNear = 10f, hipFar = 25f, kneeFar = -6f,
            shoulderNear = 24f, elbowNear = -14f, shoulderFar = -24f, elbowFar = 14f,
            driftY = 0.014f,
        ),
        PoseAngles(
            hipNear = 20f, kneeNear = -55f, hipFar = 4f, kneeFar = 0f,
            shoulderNear = 6f, elbowNear = -8f, shoulderFar = -6f, elbowFar = 8f,
            driftY = -0.004f,
        ),
    )

    /**
     * Wind-up, swing, impact, recovery.
     *
     * The weapon hand travels from high and behind the near shoulder to low and
     * across the body. Both arms move together: a one-handed weapon still gets
     * a counterbalancing far arm, and without it the figure reads as reaching
     * rather than as striking.
     */
    private val attack = listOf(
        // Drawn out, the first version of this reached *forward* at head
        // height: the forearm bent back towards the near side, which put the
        // hand in front of the face rather than behind the shoulder. A wind-up
        // needs the hand up and towards the FAR side, past vertical.
        PoseAngles(
            shoulderNear = 200f, elbowNear = 16f, shoulderFar = 186f, elbowFar = 20f,
            hipNear = 12f, kneeNear = -8f, hipFar = -16f, kneeFar = 14f,
            lean = -10f, headTilt = -4f,
        ),
        PoseAngles(
            shoulderNear = 168f, elbowNear = 4f, shoulderFar = 150f, elbowFar = 8f,
            hipNear = 16f, kneeNear = -6f, hipFar = -14f, kneeFar = 16f,
            lean = -2f,
        ),
        PoseAngles(
            shoulderNear = 80f, elbowNear = 8f, shoulderFar = 58f, elbowFar = 14f,
            hipNear = 24f, kneeNear = -6f, hipFar = -14f, kneeFar = 24f,
            lean = 14f, headTilt = 6f, driftX = 0.012f,
        ),
        PoseAngles(
            shoulderNear = 42f, elbowNear = 20f, shoulderFar = -18f, elbowFar = 22f,
            hipNear = 16f, kneeNear = -4f, hipFar = -10f, kneeFar = 16f,
            lean = 6f,
        ),
    )

    /** Gather, rise, release, settle. Symmetrical, so it never reads as a swing. */
    private val special = listOf(
        PoseAngles(
            shoulderNear = 26f, elbowNear = 108f, shoulderFar = -26f, elbowFar = -108f,
            hipNear = -12f, kneeNear = 26f, hipFar = 12f, kneeFar = 26f,
            lean = 10f, headTilt = 8f, driftY = 0.03f,
        ),
        PoseAngles(
            shoulderNear = 108f, elbowNear = 20f, shoulderFar = -108f, elbowFar = -20f,
            hipNear = -4f, kneeNear = 10f, hipFar = 4f, kneeFar = 10f,
            driftY = 0.006f,
        ),
        PoseAngles(
            shoulderNear = 150f, elbowNear = 10f, shoulderFar = -150f, elbowFar = -10f,
            lean = -8f, headTilt = -12f, driftY = -0.022f,
        ),
        PoseAngles(
            shoulderNear = 58f, elbowNear = 16f, shoulderFar = -58f, elbowFar = -16f,
            lean = 2f,
        ),
    )

    /** Snapped back, then doubled over and off balance. */
    private val hurt = listOf(
        PoseAngles(
            shoulderNear = 122f, elbowNear = -34f, shoulderFar = -122f, elbowFar = 34f,
            hipNear = -14f, kneeNear = 12f, hipFar = 16f, kneeFar = 8f,
            lean = -18f, headTilt = -16f, driftX = -0.022f,
        ),
        PoseAngles(
            shoulderNear = 44f, elbowNear = 62f, shoulderFar = -30f, elbowFar = 40f,
            hipNear = -8f, kneeNear = 24f, hipFar = -28f, kneeFar = 34f,
            lean = 20f, headTilt = 14f, driftX = -0.045f,
        ),
    )

    /** Tuck, over, out onto a knee, up. */
    private val roll = listOf(
        PoseAngles(
            shoulderNear = 32f, elbowNear = 116f, shoulderFar = -32f, elbowFar = -116f,
            hipNear = -46f, kneeNear = 104f, hipFar = -50f, kneeFar = 108f,
            lean = 34f, headTilt = 20f, driftY = 0.1f,
        ),
        PoseAngles(
            shoulderNear = 40f, elbowNear = 120f, shoulderFar = -40f, elbowFar = -120f,
            hipNear = -70f, kneeNear = 120f, hipFar = -74f, kneeFar = 124f,
            lean = 148f, driftY = 0.13f,
        ),
        PoseAngles(
            shoulderNear = 70f, elbowNear = 40f, shoulderFar = -20f, elbowFar = 60f,
            hipNear = 42f, kneeNear = -74f, hipFar = -34f, kneeFar = 96f,
            lean = 26f, driftY = 0.075f,
        ),
        PoseAngles(
            shoulderNear = 20f, elbowNear = 24f, shoulderFar = -24f, elbowFar = 20f,
            hipNear = 20f, kneeNear = -18f, hipFar = -16f, kneeFar = 30f,
            lean = 10f, driftY = 0.02f,
        ),
    )

    /** Buckling, down on a knee, folding, still. */
    private val die = listOf(
        PoseAngles(
            shoulderNear = 26f, elbowNear = 22f, shoulderFar = -30f, elbowFar = -18f,
            hipNear = -6f, kneeNear = 32f, hipFar = 8f, kneeFar = 30f,
            lean = 18f, headTilt = 14f, driftY = 0.03f,
        ),
        PoseAngles(
            shoulderNear = 18f, elbowNear = 34f, shoulderFar = -16f, elbowFar = -30f,
            hipNear = 10f, kneeNear = 20f, hipFar = -62f, kneeFar = 120f,
            lean = 28f, headTilt = 20f, driftY = 0.12f,
        ),
        PoseAngles(
            shoulderNear = 8f, elbowNear = 30f, shoulderFar = -10f, elbowFar = -26f,
            hipNear = -50f, kneeNear = 90f, hipFar = -60f, kneeFar = 100f,
            lean = 66f, headTilt = 22f, driftY = 0.2f,
        ),
        // Not quite flat: a corpse collapsed onto a single horizontal line is
        // unreadable as a body, and the guide has to be legible before it can
        // be followed.
        PoseAngles(
            shoulderNear = -58f, elbowNear = 34f, shoulderFar = -100f, elbowFar = -26f,
            hipNear = -68f, kneeNear = 40f, hipFar = -96f, kneeFar = 28f,
            lean = 78f, headTilt = 14f, driftY = 0.28f,
        ),
    )
}
