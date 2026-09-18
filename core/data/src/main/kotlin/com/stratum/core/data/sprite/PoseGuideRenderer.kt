package com.stratum.core.data.sprite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.stratum.core.domain.sprite.BoneWeight
import com.stratum.core.domain.sprite.Joint
import com.stratum.core.domain.sprite.Pose
import com.stratum.core.domain.sprite.Skeleton
import java.io.ByteArrayOutputStream

/**
 * Draws a pose as a stick figure, to be handed to the image model.
 *
 * The argument for doing this at all: prose is a poor way to specify a body.
 * "Right leg forward with the heel touching the ground, left arm swung forward"
 * is unambiguous to a person and merely suggestive to an image model — measured,
 * an attack described that way came back as a cross-body guard. A drawing of
 * the pose is not suggestive.
 *
 * Deliberately crude. A guide that is shaded, coloured or detailed invites the
 * model to reproduce *it* rather than to pose the character by it; flat black
 * lines on white are unmistakably a diagram. Small, too, because it rides
 * alongside a megabyte of character reference and a line drawing needs no
 * resolution to be read.
 */
object PoseGuideRenderer {

    fun render(pose: Pose, size: Int = GUIDE_SIZE): ByteArray? {
        if (size <= 0) return null
        val bitmap = runCatching {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val limb = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            strokeWidth = size * LIMB_WIDTH
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val torso = Paint(limb).apply { strokeWidth = size * TORSO_WIDTH }

        // Far side first, near side last, so the nearer limbs cover the further
        // ones exactly as they will in the drawing. Without it a stick figure
        // at a three-quarter angle is ambiguous about which arm is which.
        for (bone in Skeleton.bones) {
            val from = pose[bone.from] ?: continue
            val to = pose[bone.to] ?: continue
            canvas.drawLine(
                from.x * size, from.y * size, to.x * size, to.y * size,
                if (bone.weight == BoneWeight.TORSO) torso else limb,
            )
        }

        val head = pose[Joint.HEAD]
        if (head != null) {
            canvas.drawCircle(
                head.x * size, head.y * size, size * HEAD_RADIUS,
                Paint(limb).apply { style = Paint.Style.FILL },
            )
        }

        // The weapon hand marked, so the model knows which fist to close and
        // the person checking a guide can see the pipeline agrees with itself.
        val hand = pose[Joint.weaponHand]
        if (hand != null) {
            canvas.drawCircle(
                hand.x * size, hand.y * size, size * HAND_RADIUS,
                Paint().apply {
                    isAntiAlias = true
                    color = Color.BLACK
                    style = Paint.Style.FILL
                },
            )
        }

        val out = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        bitmap.recycle()
        return if (ok) out.toByteArray() else null
    }

    /** Big enough to read, small enough to ride alongside the character reference. */
    const val GUIDE_SIZE = 512

    private const val LIMB_WIDTH = 0.018f
    private const val TORSO_WIDTH = 0.032f
    private const val HEAD_RADIUS = 0.052f
    private const val HAND_RADIUS = 0.022f
    private const val PNG_QUALITY = 100
}
