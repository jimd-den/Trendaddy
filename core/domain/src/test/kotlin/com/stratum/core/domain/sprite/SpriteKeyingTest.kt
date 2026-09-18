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

    @Test
    fun `a colour that frames every cell is the canvas, and goes from inside the figure too`() {
        // The pocket between a pair of legs is background, and a flood from the
        // outside can never reach it. Knowing the grid settles what a flood
        // cannot: a colour that borders all four frames is the canvas, not the
        // costume, so it can go wherever it appears.
        val size = 40
        val white = 0xFFFFFFFF.toInt()
        val ink = 0xFF101010.toInt()
        val pixels = IntArray(size * size) { white }
        // A ring of ink in each cell of a 2x2 grid, enclosing white.
        for (cellY in 0 until 2) {
            for (cellX in 0 until 2) {
                val x0 = cellX * 20 + 5
                val y0 = cellY * 20 + 5
                for (x in x0 until x0 + 10) {
                    pixels[y0 * size + x] = ink
                    pixels[(y0 + 9) * size + x] = ink
                }
                for (y in y0 until y0 + 10) {
                    pixels[y * size + x0] = ink
                    pixels[y * size + x0 + 9] = ink
                }
            }
        }

        val blind = SpriteKeying.key(pixels, size, size)
        val knowing = SpriteKeying.key(pixels, size, size, cells = SheetGrid(2, 2))

        assertEquals(KeyStrategy.SOLID, knowing.strategy)
        assertTrue(
            knowing.clearedPixels > blind.clearedPixels,
            "knowing the grid cleared ${knowing.clearedPixels}, no more than the " +
                "${blind.clearedPixels} a flood from the outside reached",
        )
        // The enclosed pockets: 8x8 of white inside each of the four rings.
        assertEquals(blind.clearedPixels + 4 * 8 * 8, knowing.clearedPixels)
    }

    @Test
    fun `clearing the canvas leaves the figure alone, pockets and all`() {
        // The canvas rule clears a colour everywhere, so the thing it must not
        // do is take the character with it. A pale figure on a dark backdrop is
        // the case that would show it.
        val size = 40
        val navy = 0xFF101828.toInt()
        val bone = 0xFFF2E8D5.toInt()
        val pixels = IntArray(size * size) { navy }
        for (cellY in 0 until 2) {
            for (cellX in 0 until 2) {
                val x0 = cellX * 20 + 6
                val y0 = cellY * 20 + 6
                for (y in y0 until y0 + 8) {
                    for (x in x0 until x0 + 8) pixels[y * size + x] = bone
                }
            }
        }

        val keyed = SpriteKeying.key(pixels, size, size, cells = SheetGrid(2, 2))

        assertEquals(KeyStrategy.SOLID, keyed.strategy)
        val survivors = keyed.pixels.count { (it ushr 24) and 0xFF > 0 }
        assertEquals(4 * 8 * 8, survivors, "the canvas clear ate into the figures")
    }

    @Test
    fun `a grid finer than the sheet does not clear the character itself`() {
        // Flooding or clearing inside boundaries that are not there must never
        // eat the subject.
        val size = 40
        val white = 0xFFFFFFFF.toInt()
        val ink = 0xFF101010.toInt()
        val pixels = IntArray(size * size) { white }
        for (y in 12 until 28) {
            for (x in 12 until 28) pixels[y * size + x] = ink
        }

        val keyed = SpriteKeying.key(pixels, size, size, cells = SheetGrid(4, 4))

        val survivors = keyed.pixels.count { (it ushr 24) and 0xFF > 0 }
        assertEquals(16 * 16, survivors, "the flood ate into the character")
    }

    @Test
    fun `a character colour taken from the border does not get cleared everywhere`() {
        // The border of a dense sheet is not one colour: figures touch the edge
        // of their cells, so the second colour read off it is often the
        // character's own. Judging the pair together would let that colour in
        // on the backdrop's evidence and erase the character from every frame.
        val size = 60
        val white = 0xFFFFFFFF.toInt()
        val rust = 0xFFB4502A.toInt()
        val pixels = IntArray(size * size) { white }

        // A 3x3 grid. Each figure fills its cell edge to edge vertically, so
        // rust appears all along the top and bottom of the image.
        for (cellY in 0 until 3) {
            for (cellX in 0 until 3) {
                for (y in cellY * 20 until (cellY + 1) * 20) {
                    for (x in cellX * 20 + 6 until cellX * 20 + 14) {
                        pixels[y * size + x] = rust
                    }
                }
            }
        }

        val keyed = SpriteKeying.key(pixels, size, size, cells = SheetGrid(3, 3))

        val survivors = keyed.pixels.count { (it ushr 24) and 0xFF > 0 }
        assertEquals(9 * 8 * 20, survivors, "the figures were cleared along with the canvas")
    }

    @Test
    fun `keying that would leave nothing at all is refused`() {
        // The backstop. Whatever the reasoning, a fully transparent sheet draws
        // as nothing in the world and tells the player nothing about why — far
        // worse than the background it was trying to remove.
        val size = 40
        val white = 0xFFFFFFFF.toInt()
        val pixels = IntArray(size * size) { white }

        val keyed = SpriteKeying.key(pixels, size, size, cells = SheetGrid(2, 2))

        assertEquals(KeyStrategy.NONE, keyed.strategy)
        assertEquals(0, keyed.clearedPixels)
        assertTrue(
            keyed.pixels.all { (it ushr 24) and 0xFF > 0 },
            "an empty sheet was handed on as if it had been keyed",
        )
    }
}
