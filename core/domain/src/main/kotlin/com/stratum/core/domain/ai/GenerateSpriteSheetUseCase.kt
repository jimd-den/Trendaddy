package com.stratum.core.domain.ai

import com.stratum.core.domain.sprite.AnimationClip
import com.stratum.core.domain.sprite.AnimationState
import com.stratum.core.domain.sprite.SpriteOrigin
import com.stratum.core.domain.sprite.SpriteSheet

/**
 * Asks an image model for a sprite sheet.
 *
 * The model returns pixels and nothing else: it cannot be trusted to report a
 * grid, and asking it to describe its own output in JSON fails far more often
 * than the drawing does. So the grid is *dictated* in the prompt and assumed on
 * the way back, and the returned image is scaled to fit whatever it actually is.
 * That is why a low-quality generation still produces a usable sheet.
 */
class GenerateSpriteSheetUseCase(
    private val imageModel: ImageModelPort,
) {

    suspend operator fun invoke(
        request: SpriteSheetRequest,
        observer: GenerationObserver = GenerationObserver.None,
    ): Result<GeneratedSpriteSheet> {
        val layout = request.layout
        val generated = imageModel.generateImage(
            ImageRequest(
                prompt = buildPrompt(request),
                modelId = request.modelId,
                width = layout.sheetWidth,
                height = layout.sheetHeight,
                requireTransparency = true,
            ),
            observer,
        ).getOrElse { return Result.failure(it) }

        if (generated.bytes.isEmpty()) {
            return Result.failure(GenerationException("The model returned an empty image"))
        }

        val sheet = SpriteSheet(
            id = "${request.namespace}:${request.slug()}",
            name = request.subject,
            columns = layout.columns,
            rows = layout.rows,
            // Derived from the image that actually came back, not from what was
            // asked for. Models round sizes, and a sheet cut on the requested
            // dimensions would shear every frame.
            frameWidth = (generated.width / layout.columns).coerceAtLeast(1),
            frameHeight = (generated.height / layout.rows).coerceAtLeast(1),
            clips = layout.clips,
            origin = SpriteOrigin.AI_GENERATED,
        )

        return Result.success(GeneratedSpriteSheet(sheet = sheet, image = generated))
    }

    /**
     * The prompt does the heavy lifting. Image models do not follow a schema, so
     * the grid, the background and the framing are stated plainly and repeated:
     * the single most common failure is a beautiful character on an opaque
     * background, which is useless as a sprite.
     */
    private fun buildPrompt(request: SpriteSheetRequest): String {
        val layout = request.layout
        val rows = layout.clips.joinToString("\n") { clip ->
            "Row ${clip.firstFrame / layout.columns + 1}: ${clip.frameCount} frames of " +
                describe(clip.state) + "."
        }

        return buildString {
            appendLine("A 2D game sprite sheet of: ${request.subject}.")
            appendLine(request.styleDirection.ifBlank { DEFAULT_STYLE })
            appendLine()
            appendLine("Layout, exactly:")
            appendLine("- A strict grid of ${layout.columns} columns by ${layout.rows} rows.")
            appendLine("- Every frame the same size, evenly spaced, no gaps, no padding, no borders.")
            appendLine("- One character per cell, centred, feet near the bottom of the cell.")
            appendLine("- Consistent size and colours across every frame.")
            appendLine(rows)
            appendLine()
            // Spelled out because models reliably answer a request for
            // transparency by *drawing* the grey checkerboard that represents
            // it in an image editor. Saying "no checkerboard" once is not
            // enough; naming the mistake is.
            appendLine("Background: real alpha transparency, not a picture of it.")
            appendLine("Do NOT draw the grey and white checkerboard pattern that image editors")
            appendLine("use to show transparency. Do not fill the background with any colour,")
            appendLine("white or black included. No scenery, no ground shadow, no drop shadow.")
            appendLine("Do not label the frames. Do not draw a grid or guide lines.")
            appendLine("Viewed from a three-quarter overhead angle, as in an isometric game.")
        }
    }

    private fun describe(state: AnimationState): String = when (state) {
        AnimationState.IDLE -> "standing still, breathing"
        AnimationState.WALK -> "a walk cycle"
        AnimationState.ATTACK -> "swinging a basic attack, wind-up to follow-through"
        AnimationState.SPECIAL -> "unleashing a signature power, clearly different from the basic attack"
        AnimationState.HURT -> "recoiling from a hit"
        AnimationState.ROLL -> "diving into a roll"
        AnimationState.DIE -> "falling and collapsing"
    }

    private companion object {
        const val DEFAULT_STYLE =
            "Pixel art, limited palette, bold readable silhouette, dark outline."
    }
}

data class SpriteSheetRequest(
    /** What to draw: "a bronze-masked warrior with a curved blade". */
    val subject: String,
    val namespace: String = "generated",
    val styleDirection: String = "",
    val layout: SheetLayout = SheetLayout.standard(),
    val modelId: String? = null,
) {
    fun slug(): String =
        subject.lowercase().replace(NON_ID, "_").trim('_').take(MAX_SLUG).ifBlank { "sprite" }

    private companion object {
        val NON_ID = Regex("[^a-z0-9]+")
        const val MAX_SLUG = 24
    }
}

/**
 * The grid the model is asked for and the clips that read it back.
 *
 * Kept small deliberately. A 4x4 sheet at 64px a frame is something a mediocre
 * image model can actually hold together; asking for sixteen columns of
 * consistent character art reliably produces mush.
 */
data class SheetLayout(
    val columns: Int,
    val rows: Int,
    val frameSize: Int,
    val clips: List<AnimationClip>,
) {
    val sheetWidth: Int get() = columns * frameSize
    val sheetHeight: Int get() = rows * frameSize

    companion object {
        /**
         * The default for a playable character: six frames a row, one row per
         * state, including a special separate from a basic attack.
         *
         * Denser than it was. Four frames is the fewest that reads as motion at
         * all, and it showed — a walk looked like a stutter and an attack was
         * two poses. Six is enough for a swing to wind up, land and recover.
         */
        fun detailed(frameSize: Int = 64): SheetLayout = SheetLayout(
            columns = 6,
            rows = 7,
            frameSize = frameSize,
            clips = listOf(
                AnimationClip(AnimationState.IDLE, firstFrame = 0, frameCount = 6, frameDurationMs = 200),
                AnimationClip(AnimationState.WALK, firstFrame = 6, frameCount = 6, frameDurationMs = 110),
                AnimationClip(AnimationState.ATTACK, firstFrame = 12, frameCount = 6, frameDurationMs = 70, loops = false),
                AnimationClip(AnimationState.SPECIAL, firstFrame = 18, frameCount = 6, frameDurationMs = 80, loops = false),
                AnimationClip(AnimationState.HURT, firstFrame = 24, frameCount = 6, frameDurationMs = 90, loops = false),
                AnimationClip(AnimationState.ROLL, firstFrame = 30, frameCount = 6, frameDurationMs = 60, loops = false),
                AnimationClip(AnimationState.DIE, firstFrame = 36, frameCount = 6, frameDurationMs = 130, loops = false),
            ),
        )

        /** Idle, walk, attack, hurt: one row each. */
        fun standard(frameSize: Int = 64): SheetLayout = SheetLayout(
            columns = 4,
            rows = 4,
            frameSize = frameSize,
            clips = listOf(
                AnimationClip(AnimationState.IDLE, firstFrame = 0, frameCount = 4, frameDurationMs = 220),
                AnimationClip(AnimationState.WALK, firstFrame = 4, frameCount = 4, frameDurationMs = 120),
                AnimationClip(AnimationState.ATTACK, firstFrame = 8, frameCount = 4, frameDurationMs = 90, loops = false),
                AnimationClip(AnimationState.HURT, firstFrame = 12, frameCount = 2, frameDurationMs = 110, loops = false),
            ),
        )

        /** Two rows, for a monster that only needs to idle and walk. */
        fun simple(frameSize: Int = 64): SheetLayout = SheetLayout(
            columns = 4,
            rows = 2,
            frameSize = frameSize,
            clips = listOf(
                AnimationClip(AnimationState.IDLE, firstFrame = 0, frameCount = 4, frameDurationMs = 240),
                AnimationClip(AnimationState.WALK, firstFrame = 4, frameCount = 4, frameDurationMs = 130),
            ),
        )
    }
}

/** The sheet's description plus the pixels it was cut from. */
data class GeneratedSpriteSheet(
    val sheet: SpriteSheet,
    val image: GeneratedImage,
)
