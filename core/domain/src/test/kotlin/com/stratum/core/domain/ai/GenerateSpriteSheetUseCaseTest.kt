package com.stratum.core.domain.ai

import com.stratum.core.domain.sprite.AnimationState
import com.stratum.core.domain.sprite.SpriteOrigin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GenerateSpriteSheetUseCaseTest {

    private class FakeImageModel(
        private val reply: Result<GeneratedImage>,
    ) : ImageModelPort {
        var lastRequest: ImageRequest? = null
        override suspend fun generateImage(
            request: ImageRequest,
            observer: GenerationObserver,
        ): Result<GeneratedImage> {
            lastRequest = request
            observer.onStage(GenerationStage.SENDING)
            return reply
        }
    }

    private fun image(width: Int, height: Int, bytes: ByteArray = ByteArray(16) { 1 }) =
        GeneratedImage(bytes = bytes, mimeType = "image/png", width = width, height = height)

    @Test
    fun `a returned image becomes a cuttable sheet`() = runTest {
        val model = FakeImageModel(Result.success(image(256, 256)))
        val result = GenerateSpriteSheetUseCase(model)(
            SpriteSheetRequest(subject = "a bronze-masked warrior", namespace = "igbo"),
        ).getOrThrow()

        assertEquals("igbo:a_bronze_masked_warrior", result.sheet.id)
        assertEquals(4, result.sheet.columns)
        assertEquals(4, result.sheet.rows)
        assertEquals(64, result.sheet.frameWidth)
        assertEquals(64, result.sheet.frameHeight)
        assertEquals(SpriteOrigin.AI_GENERATED, result.sheet.origin)
        assertTrue(result.sheet.isCoherent)
    }

    @Test
    fun `frame size comes from the image that arrived, not the one requested`() = runTest {
        // Models round to their own supported sizes. Cutting on the requested
        // dimensions would shear every frame.
        val model = FakeImageModel(Result.success(image(512, 512)))
        val result = GenerateSpriteSheetUseCase(model)(
            SpriteSheetRequest(subject = "a leopard"),
        ).getOrThrow()

        assertEquals(128, result.sheet.frameWidth)
        assertEquals(128, result.sheet.frameHeight)
    }

    @Test
    fun `an absurdly small image still yields a sheet rather than dividing to zero`() = runTest {
        val model = FakeImageModel(Result.success(image(2, 2)))
        val result = GenerateSpriteSheetUseCase(model)(
            SpriteSheetRequest(subject = "a speck"),
        ).getOrThrow()

        assertTrue(result.sheet.frameWidth >= 1)
        assertTrue(result.sheet.frameHeight >= 1)
    }

    @Test
    fun `the sheet carries the clips the layout promised`() = runTest {
        val model = FakeImageModel(Result.success(image(256, 256)))
        val result = GenerateSpriteSheetUseCase(model)(
            SpriteSheetRequest(subject = "a warrior"),
        ).getOrThrow()

        val states = result.sheet.clips.map { it.state }.toSet()
        assertEquals(
            setOf(AnimationState.IDLE, AnimationState.WALK, AnimationState.ATTACK, AnimationState.HURT),
            states,
        )
        assertEquals(false, result.sheet.clip(AnimationState.ATTACK)!!.loops)
    }

    @Test
    fun `a simple layout asks for a smaller sheet`() = runTest {
        val model = FakeImageModel(Result.success(image(256, 128)))
        GenerateSpriteSheetUseCase(model)(
            SpriteSheetRequest(subject = "a rat", layout = SheetLayout.simple()),
        ).getOrThrow()

        assertEquals(256, model.lastRequest!!.width)
        assertEquals(128, model.lastRequest!!.height)
    }

    @Test
    fun `the prompt dictates the grid and demands transparency`() = runTest {
        val model = FakeImageModel(Result.success(image(256, 256)))
        GenerateSpriteSheetUseCase(model)(
            SpriteSheetRequest(subject = "a storm wisp", styleDirection = "chunky pixel art"),
        ).getOrThrow()

        val prompt = model.lastRequest!!.prompt
        assertTrue(prompt.contains("a storm wisp"))
        assertTrue(prompt.contains("chunky pixel art"))
        assertTrue(prompt.contains("4 columns by 4 rows"))
        // The single most common failure is an opaque background.
        assertTrue(prompt.contains("transparent", ignoreCase = true))
        assertTrue(model.lastRequest!!.requireTransparency)
    }

    @Test
    fun `the prompt describes each row's animation`() = runTest {
        val model = FakeImageModel(Result.success(image(256, 256)))
        GenerateSpriteSheetUseCase(model)(SpriteSheetRequest(subject = "a warrior")).getOrThrow()

        val prompt = model.lastRequest!!.prompt
        assertTrue(prompt.contains("Row 1"))
        assertTrue(prompt.contains("walk cycle"))
        assertTrue(prompt.contains("swinging an attack"))
    }

    @Test
    fun `an empty image is a failure, not a sheet with no pixels`() = runTest {
        val model = FakeImageModel(Result.success(image(256, 256, bytes = ByteArray(0))))
        val result = GenerateSpriteSheetUseCase(model)(SpriteSheetRequest(subject = "nothing"))

        assertTrue(result.isFailure)
        assertIs<GenerationException>(result.exceptionOrNull())
    }

    @Test
    fun `a transport failure passes through untouched`() = runTest {
        val boom = java.io.IOException("no network")
        val model = FakeImageModel(Result.failure(boom))
        val result = GenerateSpriteSheetUseCase(model)(SpriteSheetRequest(subject = "a warrior"))

        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `subjects become safe ids`() {
        assertEquals("sprite", SpriteSheetRequest(subject = "!!!").slug())
        assertEquals("a_thing", SpriteSheetRequest(subject = "  A Thing!  ").slug())
        assertTrue(SpriteSheetRequest(subject = "x".repeat(200)).slug().length <= 24)
    }
}
