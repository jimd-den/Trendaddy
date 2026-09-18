package com.stratum.core.data.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.stratum.core.domain.sprite.KeyStrategy
import com.stratum.core.domain.sprite.SpriteKeying
import java.io.ByteArrayOutputStream

/** What keying did to a sheet, for the forge to report. */
data class KeyedImage(
    val bytes: ByteArray,
    val strategy: KeyStrategy,
    val clearedPixels: Int,
) {
    val changed: Boolean get() = clearedPixels > 0

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is KeyedImage &&
                strategy == other.strategy &&
                clearedPixels == other.clearedPixels &&
                bytes.contentEquals(other.bytes))

    override fun hashCode(): Int =
        bytes.contentHashCode() * 31 * 31 + strategy.hashCode() * 31 + clearedPixels
}

/**
 * Runs [SpriteKeying] over encoded image bytes.
 *
 * The decision of *what* is background lives in the domain; this only decodes,
 * hands over pixels, and re-encodes. Always PNG on the way out — a keyed sheet
 * saved as JPEG would throw away the alpha it just gained.
 */
object SpriteBackgroundKeyer {

    fun key(bytes: ByteArray): KeyedImage {
        val decoded = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions())
        }.getOrNull() ?: return KeyedImage(bytes, KeyStrategy.NONE, 0)

        val width = decoded.width
        val height = decoded.height
        if (width <= 0 || height <= 0) return KeyedImage(bytes, KeyStrategy.NONE, 0)

        val pixels = IntArray(width * height)
        decoded.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = SpriteKeying.key(pixels, width, height)
        if (!result.cleared) {
            decoded.recycle()
            return KeyedImage(bytes, result.strategy, 0)
        }

        val keyed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        keyed.setPixels(result.pixels, 0, width, 0, 0, width, height)
        decoded.recycle()

        val out = ByteArrayOutputStream()
        val encoded = keyed.compress(Bitmap.CompressFormat.PNG, 100, out)
        keyed.recycle()

        // A failed re-encode leaves the original rather than a blank file: a
        // sheet with a background is worse art, a sheet of nothing is a bug.
        return if (encoded) {
            KeyedImage(out.toByteArray(), result.strategy, result.clearedPixels)
        } else {
            KeyedImage(bytes, KeyStrategy.NONE, 0)
        }
    }

    private fun decodeOptions() = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        // Some providers return a pre-multiplied image; reading raw pixels back
        // needs it un-multiplied or every keyed edge darkens.
        inPremultiplied = false
        inMutable = false
    }
}
