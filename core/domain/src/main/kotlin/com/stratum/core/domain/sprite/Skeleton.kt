package com.stratum.core.domain.sprite

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A point in a frame, as a fraction of it: 0,0 top-left, 1,1 bottom-right. */
data class JointPoint(val x: Float, val y: Float) {
    operator fun plus(other: JointPoint) = JointPoint(x + other.x, y + other.y)

    fun distanceTo(other: JointPoint): Float = hypot(x - other.x, y - other.y)
}

/** The joints a stick figure needs to describe a body doing something. */
enum class Joint {
    HEAD,
    NECK,
    CHEST,
    PELVIS,
    SHOULDER_NEAR, ELBOW_NEAR, HAND_NEAR,
    SHOULDER_FAR, ELBOW_FAR, HAND_FAR,
    HIP_NEAR, KNEE_NEAR, FOOT_NEAR,
    HIP_FAR, KNEE_FAR, FOOT_FAR,
    ;

    companion object {
        /**
         * Near and far rather than left and right.
         *
         * Everything here is drawn at one three-quarter camera, so one side of
         * the body is always closer to the viewer than the other. Naming them
         * that way means a pose never has to be rewritten when the character
         * mirrors: the near arm is still the near arm, and the weapon is still
         * in the hand the viewer can see.
         */
        val weaponHand = HAND_NEAR
        val weaponElbow = ELBOW_NEAR
    }
}

/** Two joints and the line between them, for drawing the figure. */
data class Bone(val from: Joint, val to: Joint, val weight: BoneWeight = BoneWeight.LIMB)

/** How thickly a bone is drawn. A torso reads as a torso only if it is thicker than an arm. */
enum class BoneWeight { TORSO, LIMB }

/**
 * A body, resolved to positions.
 *
 * The output of posing rather than the thing that is authored: angles are what
 * a person can reason about ("the elbow is bent ninety degrees"), and positions
 * are what a renderer and a weapon anchor need.
 */
data class Pose(val joints: Map<Joint, JointPoint>) {

    operator fun get(joint: Joint): JointPoint? = joints[joint]

    fun require(joint: Joint): JointPoint =
        joints[joint] ?: error("Pose is missing $joint")

    /** The lowest drawn point, which is what stands on the ground. */
    val groundY: Float
        get() = maxOf(require(Joint.FOOT_NEAR).y, require(Joint.FOOT_FAR).y)

    /**
     * Where the weapon hand is, and which way the forearm points.
     *
     * The whole reason a skeleton is worth having on the rendering side. A
     * weapon held in a hand points along the forearm, and both of those are
     * facts the skeleton already knows exactly — where previously they were
     * authored by guesswork and then corrected per character by hand.
     */
    fun weaponGrip(): WeaponGrip {
        val hand = require(Joint.weaponHand)
        val elbow = require(Joint.weaponElbow)
        val forearm = degreesFromDown(hand.x - elbow.x, hand.y - elbow.y)
        return WeaponGrip(
            at = hand,
            forearmDegrees = forearm,
            weaponDegrees = weaponDegreesFor(forearm),
        )
    }

    private companion object {
        /**
         * The one place the two angle conventions meet.
         *
         * Limbs are authored anticlockwise from straight down, because that is
         * what reads naturally when writing a pose: an arm out to the near side
         * is +90. [WeaponAnchor] measures clockwise from straight up, because
         * that is what the renderer's rotate() does to a weapon drawn pointing
         * up. Those are opposite handedness from different zeroes, so the
         * conversion is a reflection rather than an offset -- which is exactly
         * the kind of thing that should exist once, with its reasoning attached,
         * instead of being rediscovered at three call sites.
         */
        fun weaponDegreesFor(forearmDegrees: Float): Float = normalized(180f - forearmDegrees)

        /**
         * Brought into (-180, 180].
         *
         * A rotation of 324 and one of -36 draw identically, so this changes
         * nothing on screen — and everything about being able to reason. Signed
         * around zero, a wind-up is negative and a follow-through positive, so
         * an arc that turns one way reads as a rising sequence instead of one
         * that appears to leap backwards every time it crosses vertical.
         */
        fun normalized(degrees: Float): Float {
            var value = degrees % 360f
            if (value > 180f) value -= 360f
            if (value <= -180f) value += 360f
            return value
        }
    }
}

/**
 * A hand, the direction the arm reaches it from, and what that means for a
 * weapon held in it.
 *
 * A weapon in a hand continues the line of the forearm. That is the fact this
 * whole idea turns on: both numbers are things the skeleton knows exactly,
 * where before they were authored by guesswork and then corrected per character
 * by hand.
 */
data class WeaponGrip(
    val at: JointPoint,
    val forearmDegrees: Float,
    /** Ready for [WeaponAnchor]: clockwise from a weapon drawn pointing up. */
    val weaponDegrees: Float,
)

/**
 * The proportions of a figure, as fractions of its full height.
 *
 * Fixed rather than per-character on purpose. The figure this describes is not
 * the character — it is the stick guide the character is drawn *onto*, and a
 * guide whose proportions drifted per character would stop being a common
 * reference. Characters differ in what they wear, not in having one head.
 */
data class Skeleton(
    val pelvisY: Float = 0.54f,
    val pelvisToChest: Float = 0.16f,
    val chestToNeck: Float = 0.12f,
    val neckToHead: Float = 0.09f,
    val shoulderHalfWidth: Float = 0.1f,
    val hipHalfWidth: Float = 0.055f,
    val upperArm: Float = 0.15f,
    val foreArm: Float = 0.14f,
    val thigh: Float = 0.22f,
    val shin: Float = 0.21f,
    /**
     * How far the far side of the body is shifted towards the viewer's left.
     *
     * The cheapest possible nod to the three-quarter camera, and enough: a
     * stick figure with both sides exactly aligned reads as a flat front view,
     * which is the one angle the art must not come back as.
     */
    val threeQuarterSkew: Float = 0.045f,
) {

    /**
     * Resolves angles into positions.
     *
     * Ordinary forward kinematics, and the reason poses are authored as angles:
     * bone lengths are fixed here, so no authored pose can stretch a forearm or
     * shrink a thigh. A pose expressed as raw coordinates can do both, and the
     * result is a guide that teaches the model the wrong proportions.
     */
    fun pose(angles: PoseAngles): Pose {
        val pelvis = JointPoint(0.5f + angles.driftX, pelvisY + angles.driftY)
        val chest = pelvis.along(pelvisToChest, UP + angles.lean)
        val neck = chest.along(chestToNeck, UP + angles.lean * 0.5f)
        val head = neck.along(neckToHead, UP + angles.headTilt)

        val shoulderNear = JointPoint(chest.x + shoulderHalfWidth, chest.y)
        val shoulderFar = JointPoint(chest.x - shoulderHalfWidth + threeQuarterSkew, chest.y)
        val hipNear = JointPoint(pelvis.x + hipHalfWidth, pelvis.y)
        val hipFar = JointPoint(pelvis.x - hipHalfWidth + threeQuarterSkew, pelvis.y)

        val elbowNear = shoulderNear.along(upperArm, angles.shoulderNear)
        val handNear = elbowNear.along(foreArm, angles.shoulderNear + angles.elbowNear)
        val elbowFar = shoulderFar.along(upperArm, angles.shoulderFar)
        val handFar = elbowFar.along(foreArm, angles.shoulderFar + angles.elbowFar)

        val kneeNear = hipNear.along(thigh, angles.hipNear)
        val footNear = kneeNear.along(shin, angles.hipNear + angles.kneeNear)
        val kneeFar = hipFar.along(thigh, angles.hipFar)
        val footFar = kneeFar.along(shin, angles.hipFar + angles.kneeFar)

        return Pose(
            mapOf(
                Joint.PELVIS to pelvis,
                Joint.CHEST to chest,
                Joint.NECK to neck,
                Joint.HEAD to head,
                Joint.SHOULDER_NEAR to shoulderNear, Joint.ELBOW_NEAR to elbowNear,
                Joint.HAND_NEAR to handNear,
                Joint.SHOULDER_FAR to shoulderFar, Joint.ELBOW_FAR to elbowFar,
                Joint.HAND_FAR to handFar,
                Joint.HIP_NEAR to hipNear, Joint.KNEE_NEAR to kneeNear,
                Joint.FOOT_NEAR to footNear,
                Joint.HIP_FAR to hipFar, Joint.KNEE_FAR to kneeFar, Joint.FOOT_FAR to footFar,
            ),
        ).clampedToGround()
    }

    /**
     * Lifts a figure whose lowest point has gone through the floor.
     *
     * A clamp rather than a snap, and the difference matters. Snapping every
     * pose so its lowest point touches the ground would cancel the very thing a
     * walk is made of — the body rises over the planted foot and falls at each
     * contact — and leave a figure gliding along at a constant height. Clamping
     * only rescues the poses that overreach, which drawn out is most of the
     * deep ones: a lunge and a stride both put a foot below where a standing
     * figure's feet were, and a guide with a foot off the bottom of the frame
     * teaches the model to crop the character.
     */
    private fun Pose.clampedToGround(): Pose {
        val lowest = joints.values.maxOf { it.y }
        if (lowest <= GROUND) return this
        val lift = lowest - GROUND
        return Pose(joints.mapValues { (_, point) -> JointPoint(point.x, point.y - lift) })
    }

    /** How long a bone should be, for a test or a renderer that wants to check. */
    fun lengthOf(bone: Bone): Float = when (bone.from to bone.to) {
        Joint.SHOULDER_NEAR to Joint.ELBOW_NEAR, Joint.SHOULDER_FAR to Joint.ELBOW_FAR -> upperArm
        Joint.ELBOW_NEAR to Joint.HAND_NEAR, Joint.ELBOW_FAR to Joint.HAND_FAR -> foreArm
        Joint.HIP_NEAR to Joint.KNEE_NEAR, Joint.HIP_FAR to Joint.KNEE_FAR -> thigh
        Joint.KNEE_NEAR to Joint.FOOT_NEAR, Joint.KNEE_FAR to Joint.FOOT_FAR -> shin
        Joint.PELVIS to Joint.CHEST -> pelvisToChest
        Joint.CHEST to Joint.NECK -> chestToNeck
        Joint.NECK to Joint.HEAD -> neckToHead
        else -> 0f
    }

    companion object {
        /** Straight up, in the angle convention below. */
        const val UP = 180f

        /** Where the floor is. Nothing drawn may go below it. */
        const val GROUND = 0.96f

        /** The lines to draw, far side first so the near side covers it. */
        val bones: List<Bone> = listOf(
            Bone(Joint.SHOULDER_FAR, Joint.ELBOW_FAR),
            Bone(Joint.ELBOW_FAR, Joint.HAND_FAR),
            Bone(Joint.HIP_FAR, Joint.KNEE_FAR),
            Bone(Joint.KNEE_FAR, Joint.FOOT_FAR),
            Bone(Joint.PELVIS, Joint.CHEST, BoneWeight.TORSO),
            Bone(Joint.CHEST, Joint.NECK, BoneWeight.TORSO),
            Bone(Joint.NECK, Joint.HEAD, BoneWeight.TORSO),
            Bone(Joint.SHOULDER_FAR, Joint.SHOULDER_NEAR, BoneWeight.TORSO),
            Bone(Joint.HIP_FAR, Joint.HIP_NEAR, BoneWeight.TORSO),
            Bone(Joint.SHOULDER_NEAR, Joint.ELBOW_NEAR),
            Bone(Joint.ELBOW_NEAR, Joint.HAND_NEAR),
            Bone(Joint.HIP_NEAR, Joint.KNEE_NEAR),
            Bone(Joint.KNEE_NEAR, Joint.FOOT_NEAR),
        )
    }
}

/**
 * One pose, as the angle of every joint.
 *
 * Degrees clockwise from straight down, which sounds odd until you author with
 * it: an arm hanging at the side is 0, an arm straight out to the near side is
 * 90, an arm raised overhead is 180. Elbows and knees are relative to the limb
 * above them, so bending an elbow does not require recomputing where the
 * shoulder put it.
 */
data class PoseAngles(
    val shoulderNear: Float = 8f,
    val elbowNear: Float = 0f,
    val shoulderFar: Float = -8f,
    val elbowFar: Float = 0f,
    val hipNear: Float = 2f,
    val kneeNear: Float = 0f,
    val hipFar: Float = -2f,
    val kneeFar: Float = 0f,
    val lean: Float = 0f,
    val headTilt: Float = 0f,
    /** Moves the whole body, for a crouch, a lunge or a fall. */
    val driftX: Float = 0f,
    val driftY: Float = 0f,
) {
    /**
     * Part way from this pose to [other].
     *
     * Every field is an angle or an offset, so a straight blend is the right
     * one. It exists because the authored poses and the frames asked for are
     * two different counts that will not always agree: a model asked for six
     * frames may send five, and without a blend the rig snaps between authored
     * poses, holding several frames identical and then jumping -- which reads
     * as the character freezing and twitching rather than moving.
     */
    fun blendedTo(other: PoseAngles, amount: Float): PoseAngles {
        val t = amount.coerceIn(0f, 1f)
        if (t <= 0f) return this
        if (t >= 1f) return other
        fun mix(a: Float, b: Float) = a + (b - a) * t
        return PoseAngles(
            shoulderNear = mix(shoulderNear, other.shoulderNear),
            elbowNear = mix(elbowNear, other.elbowNear),
            shoulderFar = mix(shoulderFar, other.shoulderFar),
            elbowFar = mix(elbowFar, other.elbowFar),
            hipNear = mix(hipNear, other.hipNear),
            kneeNear = mix(kneeNear, other.kneeNear),
            hipFar = mix(hipFar, other.hipFar),
            kneeFar = mix(kneeFar, other.kneeFar),
            lean = mix(lean, other.lean),
            headTilt = mix(headTilt, other.headTilt),
            driftX = mix(driftX, other.driftX),
            driftY = mix(driftY, other.driftY),
        )
    }
}

/** Rotates a unit vector [degrees] clockwise from straight down and steps along it. */
private fun JointPoint.along(length: Float, degrees: Float): JointPoint {
    val radians = Math.toRadians(degrees.toDouble())
    // Down is (0, 1) because y grows downward; rotating clockwise on screen
    // sends it towards +x first, which is why "out to the near side" is +90.
    return JointPoint(
        x = x + (length * sin(radians)).toFloat(),
        y = y + (length * cos(radians)).toFloat(),
    )
}

/** The inverse: which angle a direction corresponds to. */
internal fun degreesFromDown(dx: Float, dy: Float): Float {
    if (abs(dx) < 1e-6f && abs(dy) < 1e-6f) return 0f
    val degrees = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dy.toDouble())).toFloat()
    return degrees
}

/** Unused directly, kept so the convention above stays honest under test. */
internal fun directionFromDown(degrees: Float): Pair<Float, Float> {
    val radians = Math.toRadians(degrees.toDouble())
    return sin(radians).toFloat() to cos(radians).toFloat()
}
