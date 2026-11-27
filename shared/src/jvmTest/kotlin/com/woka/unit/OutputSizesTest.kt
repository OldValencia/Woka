package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.tools.resize.SizeLimits
import com.woka.feature.editor.tools.resize.photoSizePresets
import com.woka.feature.editor.tools.resize.videoSizePresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The size row's arithmetic (RD-401): what each source offers, what the renderers accept,
 * and the other side of a linked field.
 */
class OutputSizesTest {

    private fun video(width: Int, height: Int) =
        videoSizePresets(IntSize(width, height)).map { it.label to it.size }

    @Test
    fun `a photo offers its frame and three proportions of it`() {
        val presets = photoSizePresets(IntSize(4032, 3024))

        assertEquals(listOf("Original", "75%", "50%", "25%"), presets.map { it.label })
        assertEquals(IntSize(2016, 1512), presets[2].size)
        // Portrait keeps its orientation: proportions of each side, not of a long edge.
        assertEquals(IntSize(756, 1008), photoSizePresets(IntSize(3024, 4032))[3].size)
    }

    @Test
    fun `a 1080p film offers 720p and 480p`() {
        assertEquals(
            listOf("Original" to IntSize(1920, 1080), "720p" to IntSize(1280, 720), "480p" to IntSize(854, 480)),
            video(1920, 1080)
        )
    }

    /** The case the author raised: no second 720p beside an Original that is one. */
    @Test
    fun `a 720p film offers 480p alone`() {
        assertEquals(listOf("Original" to IntSize(1280, 720), "480p" to IntSize(854, 480)), video(1280, 720))
    }

    @Test
    fun `a 4K film offers the two largest tiers below it`() {
        assertEquals(listOf("Original", "1440p", "1080p"), video(3840, 2160).map { it.first })
    }

    @Test
    fun `a film at or below 480p offers Original alone`() {
        assertEquals(listOf("Original"), video(854, 480).map { it.first })
        assertEquals(listOf("Original"), video(640, 360).map { it.first })
    }

    /** Bounds swap for portrait; a size off the tier's own reads as pixels, not as `720p`. */
    @Test
    fun `a portrait or non-16-9 film is fitted and named by its pixels`() {
        assertEquals(
            listOf("Original" to IntSize(1080, 1920), "720p" to IntSize(720, 1280), "480p" to IntSize(480, 854)),
            video(1080, 1920)
        )
        assertEquals(
            listOf("Original" to IntSize(1440, 1080), "960 × 720" to IntSize(960, 720), "640 × 480" to IntSize(640, 480)),
            video(1440, 1080)
        )
    }

    @Test
    fun `a photo takes 1 to 16384 a side, enlarging included`() {
        assertTrue(SizeLimits.Photo.accepts(IntSize(1, 16384)))
        assertTrue(SizeLimits.Photo.accepts(IntSize(9000, 7000)))
        assertFalse(SizeLimits.Photo.accepts(IntSize(0, 100)))
        assertFalse(SizeLimits.Photo.accepts(IntSize(16385, 100)))
    }

    @Test
    fun `a film takes even sides within 4096 by 2304, either way round`() {
        assertTrue(SizeLimits.Video.accepts(IntSize(4096, 2304)))
        assertTrue(SizeLimits.Video.accepts(IntSize(2304, 4096)))
        assertFalse(SizeLimits.Video.accepts(IntSize(1001, 720)), "an odd side reached the encoder")
        assertFalse(SizeLimits.Video.accepts(IntSize(4098, 2000)))
        assertFalse(SizeLimits.Video.accepts(IntSize(3000, 2400)))
    }

    @Test
    fun `a linked side keeps the frame's shape, even for a film`() {
        assertEquals(750, SizeLimits.Photo.linked(1000, 4000, 3000))
        assertEquals(563, SizeLimits.Photo.linked(1001, 1920, 1080))
        assertEquals(564, SizeLimits.Video.linked(1001, 1920, 1080))
    }
}
