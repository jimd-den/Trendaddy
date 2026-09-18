package com.stratum.core.domain.ai

import com.stratum.core.domain.sprite.AnimationState
import com.stratum.core.domain.sprite.PoseCell
import com.stratum.core.domain.sprite.PoseSheetPlanner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingImageModel(
    private val reply: Result<GeneratedImage> = Result.success(
        GeneratedImage(ByteArray(16) { 7 }, "image/png", 1024, 1024),
    ),
) : ImageModelPort {
    val requests = mutableListOf<ImageRequest>()
    override suspend fun generateImage(
        request: ImageRequest,
        observer: GenerationObserver,
    ): Result<GeneratedImage> {
        requests += request
        return reply
    }
}

class PoseScriptTest {

    @Test
    fun `a script runs the states in the canonical order`() {
        val script = PoseScript.of(listOf(AnimationState.DIE, AnimationState.IDLE, AnimationState.WALK))

        assertEquals(
            listOf(AnimationState.IDLE, AnimationState.WALK, AnimationState.DIE),
            script.states,
        )
    }

    @Test
    fun `a walk is four poses and a hurt is two`() {
        val script = PoseScript.full()
        assertEquals(4, script.stepsFor(AnimationState.WALK).size)
        assertEquals(2, script.stepsFor(AnimationState.HURT).size)
        assertEquals(script.steps.size, script.frameCounts().values.sum())
    }

    @Test
    fun `every pose describes a body rather than a character`() {
        // The identity comes from the reference image. A step that described
        // the character would invite the model to redraw it, which is the drift
        // this whole pipeline exists to avoid.
        PoseScript.full().steps.forEach { step ->
            assertFalse(step.instruction.isBlank(), "${step.key} has no instruction")
            assertFalse(
                "character" in step.instruction.lowercase(),
                "${step.key} describes the character: ${step.instruction}",
            )
        }
    }

    @Test
    fun `a step and the cell that receives it agree on the name`() {
        val step = PoseScript.full().stepsFor(AnimationState.ATTACK)[2]
        assertEquals(PoseCell.keyOf(AnimationState.ATTACK, 2), step.key)
    }

    @Test
    fun `an interrupted run carries on from what is already drawn`() {
        val script = PoseScript.enemy()
        val done = script.steps.take(5).map { it.key }.toSet()

        assertEquals(script.steps.size - 5, script.remaining(done).size)
        assertTrue(script.remaining(done).none { it.key in done })
        assertEquals(5f / script.steps.size, script.progress(done))
    }

    @Test
    fun `an enemy script skips the states nobody watches it perform`() {
        val states = PoseScript.enemy().states
        assertTrue(AnimationState.ROLL !in states)
        assertTrue(AnimationState.DIE in states)
    }
}

class PoseSheetPlannerTest {

    @Test
    fun `poses are laid out one animation per row`() {
        val plan = assertNotNull(
            PoseSheetPlanner.plan(
                id = "hero:warrior",
                name = "Warrior",
                frameCounts = mapOf(
                    AnimationState.IDLE to 2,
                    AnimationState.WALK to 4,
                    AnimationState.DIE to 4,
                ),
                cellSize = 96,
            ),
        )

        assertEquals(4, plan.columns)
        assertEquals(3, plan.rows)
        assertEquals(384, plan.width)
        assertEquals(288, plan.height)

        val walk = assertNotNull(plan.sheet.clip(AnimationState.WALK))
        assertEquals(4, walk.firstFrame)
        assertEquals(4, walk.frameCount)
        // Short rows are padded rather than packed, so the death starts on its
        // own line instead of in the middle of the walk.
        val die = assertNotNull(plan.sheet.clip(AnimationState.DIE))
        assertEquals(8, die.firstFrame)
        assertTrue(plan.sheet.isCoherent)
    }

    @Test
    fun `every pose has a cell and knows where it lands`() {
        val plan = assertNotNull(
            PoseSheetPlanner.plan(
                id = "t",
                name = "T",
                frameCounts = mapOf(AnimationState.IDLE to 2, AnimationState.WALK to 4),
                cellSize = 64,
            ),
        )

        assertEquals(6, plan.cells.size)
        val secondWalk = assertNotNull(plan.cellFor(PoseCell.keyOf(AnimationState.WALK, 1)))
        assertEquals(1, secondWalk.row)
        assertEquals(1, secondWalk.column)
        assertEquals(5, secondWalk.cellIndex(plan.columns))

        val rect = plan.rectFor(secondWalk)
        assertEquals(64, rect.left)
        assertEquals(64, rect.top)
        assertEquals(64, rect.width)
    }

    @Test
    fun `a cell size no sheet could use is brought back into range`() {
        val huge = assertNotNull(
            PoseSheetPlanner.plan("t", "T", mapOf(AnimationState.IDLE to 1), cellSize = 4096),
        )
        assertEquals(PoseSheetPlanner.MAX_CELL, huge.cellSize)

        val tiny = assertNotNull(
            PoseSheetPlanner.plan("t", "T", mapOf(AnimationState.IDLE to 1), cellSize = 1),
        )
        assertEquals(PoseSheetPlanner.MIN_CELL, tiny.cellSize)
    }

    @Test
    fun `nothing to draw is no plan`() {
        assertNull(PoseSheetPlanner.plan("t", "T", emptyMap()))
        assertNull(PoseSheetPlanner.plan("t", "T", mapOf(AnimationState.IDLE to 0)))
    }

    @Test
    fun `the plan exists before a single pose is drawn, which is what makes it restartable`() {
        val script = PoseScript.enemy()
        val plan = assertNotNull(
            PoseSheetPlanner.plan("t", "T", script.frameCounts(), cellSize = 96),
        )
        // Every step the script will ask for already has a cell waiting, so a
        // frame that comes back wrong can be replaced on its own.
        assertTrue(script.steps.all { plan.cellFor(it.key) != null })
    }
}

class BasePoseGenerationTest {

    @Test
    fun `the reference is asked for as a T-pose with nothing overlapping`() = runTest {
        val model = RecordingImageModel()
        GenerateBasePoseUseCase(model)(
            BasePoseRequest(subject = "a bronze-masked warrior"),
        ).getOrThrow()

        val request = model.requests.single()
        assertEquals(1024, request.width)
        val prompt = request.prompt
        assertTrue("T-pose" in prompt, prompt)
        assertTrue("Nothing overlapping anything else" in prompt, prompt)
        // An editor can only preserve what it can see.
        assertTrue("fully visible and separated" in prompt, prompt)
    }

    @Test
    fun `the reference asks for flat chroma rather than pleading for alpha`() = runTest {
        val model = RecordingImageModel()
        GenerateBasePoseUseCase(model)(BasePoseRequest(subject = "a warrior")).getOrThrow()

        val prompt = model.requests.single().prompt
        assertTrue("#00FF00" in prompt, prompt)
        assertTrue("checkerboard" in prompt, prompt)
        // Nothing about grids or cells: this is one drawing.
        assertTrue("cell" !in prompt.lowercase(), prompt)
    }

    @Test
    fun `an empty image is a failure rather than a blank character`() = runTest {
        val model = RecordingImageModel(
            Result.success(GeneratedImage(ByteArray(0), "image/png", 1024, 1024)),
        )
        val result = GenerateBasePoseUseCase(model)(BasePoseRequest(subject = "a warrior"))
        assertTrue(result.isFailure)
    }
}

class PoseFrameGenerationTest {

    private val reference = ImageReference(ByteArray(32) { 3 }, "image/png")

    @Test
    fun `every frame is edited from the reference, not drawn from nothing`() = runTest {
        val model = RecordingImageModel()
        val step = PoseScript.full().stepsFor(AnimationState.WALK).first()

        GeneratePoseFrameUseCase(model)(
            PoseFrameRequest(reference = reference, step = step),
        ).getOrThrow()

        val request = model.requests.single()
        assertEquals(listOf(reference), request.references)
        assertTrue(step.instruction in request.prompt, request.prompt)
    }

    @Test
    fun `the prompt spends itself on what must not change`() = runTest {
        val model = RecordingImageModel()
        GeneratePoseFrameUseCase(model)(
            PoseFrameRequest(
                reference = reference,
                step = PoseScript.full().stepsFor(AnimationState.ATTACK).first(),
            ),
        ).getOrThrow()

        val prompt = model.requests.single().prompt
        assertTrue("The same colours, exactly" in prompt, prompt)
        assertTrue("The same scale" in prompt, prompt)
        assertTrue("Change only the pose" in prompt, prompt)
        // Effects between frames read as flicker, not as polish.
        assertTrue("motion blur" in prompt, prompt)
    }

    @Test
    fun `frames are never chained off each other`() = runTest {
        // Editing frame two from frame one and three from two compounds the
        // drift until the character is somebody else. Every frame comes off the
        // same reference.
        val model = RecordingImageModel()
        val use = GeneratePoseFrameUseCase(model)
        PoseScript.full().stepsFor(AnimationState.WALK).forEach { step ->
            use(PoseFrameRequest(reference = reference, step = step)).getOrThrow()
        }

        assertEquals(4, model.requests.size)
        assertTrue(model.requests.all { it.references == listOf(reference) })
    }

    @Test
    fun `a failed frame names the pose it was for`() = runTest {
        val model = RecordingImageModel(
            Result.success(GeneratedImage(ByteArray(0), "image/png", 1024, 1024)),
        )
        val step = PoseScript.full().stepsFor(AnimationState.DIE)[2]
        val result = GeneratePoseFrameUseCase(model)(
            PoseFrameRequest(reference = reference, step = step),
        )

        assertTrue(result.isFailure)
        assertTrue(step.key in result.exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun `a reference compares by its bytes, so what was sent can be asserted`() {
        assertEquals(ImageReference(byteArrayOf(1, 2, 3)), ImageReference(byteArrayOf(1, 2, 3)))
        assertNull(setOf(ImageReference(byteArrayOf(1))).find { it.bytes.size == 2 })
    }
}
