package com.stratum.core.domain.sprite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asking a model for a transparent background is not enough. They routinely
 * answer with the grey checkerboard that *represents* transparency in an image
 * editor — a picture of transparency rather than transparency — or with a flat
 * colour. Either way the sprite arrives as an opaque square.
 */
class SpriteKeyingTest {

    private val width = 32
    private val height = 32

    private fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private val opaqueRed = argb(255, 220, 40, 40)
    private val lightCheck = argb(255, 204, 204, 204)
    private val darkCheck = argb(255, 153, 153, 153)

    /** A sheet with a subject in the middle and [background] everywhere else. */
    private fun sheet(background: (Int, Int) -> Int, subject: Int = opaqueRed): IntArray =
        IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            if (x in 12..19 && y in 12..19) subject else background(x, y)
        }

    private fun alphaAt(pixels: IntArray, x: Int, y: Int) = (pixels[y * width + x] ushr 24) and 0xFF

    @Test
    fun `a drawn checkerboard is cleared, including between the legs`() {
        // Two gaps inside the subject, which a flood fill from the edge would
        // never reach — exactly where a character's legs leave a hole.
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val inSubject = x in 12..19 && y in 12..19
            val inGap = y in 16..19 && (x == 14 || x == 17)
            if (inSubject && !inGap) opaqueRed
            else if (((x / 4) + (y / 4)) % 2 == 0) lightCheck else darkCheck
        }

        val result = SpriteKeying.key(pixels, width, height)

        assertEquals(KeyStrategy.CHECKERBOARD, result.strategy)
        assertEquals(0, alphaAt(result.pixels, 0, 0), "the corner kept the checkerboard")
        assertEquals(0, alphaAt(result.pixels, 14, 17), "an enclosed gap kept the checkerboard")
        assertEquals(255, alphaAt(result.pixels, 13, 13), "the subject was erased")
    }

    @Test
    fun `a solid backdrop is cleared from the edges in`() {
        val teal = argb(255, 20, 140, 140)
        val pixels = sheet(background = { _, _ -> teal })

        val result = SpriteKeying.key(pixels, width, height)

        assertEquals(KeyStrategy.SOLID, result.strategy)
        assertEquals(0, alphaAt(result.pixels, 0, 0))
        assertEquals(0, alphaAt(result.pixels, 31, 31))
        assertEquals(255, alphaAt(result.pixels, 16, 16), "the subject was erased")
    }

    @Test
    fun `a solid backdrop colour inside the subject survives`() {
        // The same teal appears on the character. Only what touches the edge is
        // background; a flood fill is the whole reason this is not a blanket
        // colour replace.
        val teal = argb(255, 20, 140, 140)
        val pixels = sheet(background = { _, _ -> teal }).also {
            it[16 * width + 16] = teal
        }

        val result = SpriteKeying.key(pixels, width, height)

        assertEquals(KeyStrategy.SOLID, result.strategy)
        assertEquals(255, alphaAt(result.pixels, 16, 16), "an enclosed teal pixel was keyed out")
    }

    @Test
    fun `a sheet that already has alpha is left completely alone`() {
        val pixels = sheet(background = { _, _ -> argb(0, 0, 0, 0) })

        val result = SpriteKeying.key(pixels, width, height)

        assertEquals(KeyStrategy.ALREADY_TRANSPARENT, result.strategy)
        assertEquals(0, result.clearedPixels)
        assertTrue(result.pixels.contentEquals(pixels), "a correct sheet was modified")
    }

    @Test
    fun `a busy photographic border is not treated as a background`() {
        // No colour dominates the border, so there is nothing to key out and
        // guessing would eat the artwork.
        var seed = 1
        val pixels = IntArray(width * height) {
            seed = seed * 1103515245 + 12345
            argb(255, (seed ushr 16) and 0xFF, (seed ushr 8) and 0xFF, seed and 0xFF)
        }

        val result = SpriteKeying.key(pixels, width, height)

        assertEquals(KeyStrategy.NONE, result.strategy)
        assertEquals(0, result.clearedPixels)
    }

    @Test
    fun `keying clears alpha without changing the colour, so edges do not fringe`() {
        val teal = argb(255, 20, 140, 140)
        val result = SpriteKeying.key(sheet(background = { _, _ -> teal }), width, height)

        val corner = result.pixels[0]
        assertEquals(0, (corner ushr 24) and 0xFF)
        assertEquals(teal and 0x00FFFFFF, corner and 0x00FFFFFF, "the colour was discarded too")
    }

    @Test
    fun `a subject touching the edge is not eaten`() {
        val teal = argb(255, 20, 140, 140)
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            // A red bar running off the left edge.
            if (y in 14..17 && x <= 10) opaqueRed else teal
        }

        val result = SpriteKeying.key(pixels, width, height)

        assertEquals(255, alphaAt(result.pixels, 0, 15), "a subject bleeding off the edge was keyed out")
        assertEquals(0, alphaAt(result.pixels, 0, 0))
    }

    @Test
    fun `a degenerate image is handled rather than crashing`() {
        assertEquals(KeyStrategy.NONE, SpriteKeying.key(IntArray(0), 0, 0).strategy)
        assertEquals(KeyStrategy.NONE, SpriteKeying.key(IntArray(4), 8, 8).strategy)
    }
}
