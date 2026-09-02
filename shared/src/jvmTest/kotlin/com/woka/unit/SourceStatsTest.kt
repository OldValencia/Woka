package com.woka.unit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.woka.feature.editor.core.LUMA_BUCKETS
import com.woka.feature.editor.engine.image.sourceStatsOf
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The four numbers a tool is allowed to see (ME-166).
 *
 * Built pictures rather than a corpus file: what is asserted here is arithmetic — that
 * the histogram counts every pixel once and that the percentiles land where the levels
 * put them — and a photograph would make every expected value a magic constant nobody
 * could check by reading.
 */
class SourceStatsTest {

    private val width = 40
    private val height = 20

    private fun picture(fill: (Bitmap) -> Unit): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL))
        fill(bitmap)
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    }

    private fun grey(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    /** Every pixel counted exactly once, which is what makes the rest proportions. */
    @Test
    fun `the histogram counts every pixel`() {
        val stats = sourceStatsOf(picture { it.erase(grey(128)) })
        assertEquals(LUMA_BUCKETS, stats.luma.size)
        assertEquals(width * height, stats.luma.sum())
    }

    /** A flat picture is its own mean, its own black point and its own white point. */
    @Test
    fun `a flat grey reports itself`() {
        val stats = sourceStatsOf(picture { it.erase(grey(128)) })
        assertTrue(abs(stats.meanLuma - 128 / 255f) < 0.01f, "mean ${stats.meanLuma}")
        assertTrue(abs(stats.blackPoint - stats.whitePoint) < 0.01f, "a flat picture has a range")
    }

    /**
     * **The percentile, not the minimum.** Half the picture is lifted off black, so a
     * black point read from the darkest pixel would still say zero; read from the 0.1
     * percentile it says what the bulk of the shadows actually are.
     */
    @Test
    fun `a lifted picture reports a black point above zero`() {
        val stats = sourceStatsOf(picture { it.erase(grey(64)) })
        assertTrue(stats.blackPoint > 0.2f, "black point ${stats.blackPoint} did not follow the picture")
    }

    /** Two halves, so the two ends of the range are different numbers. */
    @Test
    fun `a split picture spans both halves`() {
        val stats = sourceStatsOf(
            picture {
                it.erase(grey(32))
                it.erase(grey(220), IRect.makeLTRB(20, 0, 40, 20))
            }
        )
        assertTrue(stats.blackPoint < 0.2f, "black point ${stats.blackPoint}")
        assertTrue(stats.whitePoint > 0.8f, "white point ${stats.whitePoint}")
        assertEquals(width * height, stats.luma.sum())
    }
}
