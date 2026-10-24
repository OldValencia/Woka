package com.woka.integration

import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.SampleMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `still.ico`/`still.cur`: 16 and 32 as BMP entries, then 256 as PNG — the largest last. */
private val ICON_FIXTURES = listOf("still.ico", "still.cur")
private const val ICON_ENTRIES = 3
private const val LARGEST_ICON = 256

/**
 * The formats photo-formats PF-002 routed to `ImageIO` through the TwelveMonkeys
 * plugins, decoded the way the viewer decodes them.
 */
class ImageIoFormatsTest {

    @Test
    fun `an icon opens on its largest entry`() {
        ICON_FIXTURES.forEach { name ->
            val file = SampleMedia.file(name)
            val decoder = stillDecoderFor(file)
            val first = decoder.frame(file, 0)
            assertEquals(LARGEST_ICON, first.width, name)
            assertEquals(LARGEST_ICON, decoder.nativeSize(file, 0).width, name)
            assertEquals(ICON_ENTRIES, decoder.frameSet(file).frames.size, name)
            assertEquals(16, decoder.nativeSize(file, ICON_ENTRIES - 1).width, name)
        }
    }

    /** The circle's corner is transparent; a BGR path would have drawn it black. */
    @Test
    fun `an icon keeps its alpha`() {
        ICON_FIXTURES.forEach { name ->
            val file = SampleMedia.file(name)
            val bitmap = stillDecoderFor(file).frame(file, 0)
            val buffer = IntArray(2)
            bitmap.readPixels(buffer, 0, 0, 1, 1)
            bitmap.readPixels(buffer, LARGEST_ICON / 2, LARGEST_ICON / 2, 1, 1, bufferOffset = 1)
            assertEquals(0, buffer[0] ushr 24, "$name corner")
            assertEquals(255, buffer[1] ushr 24, "$name centre")
        }
    }

    /** One picture, like `.psd` — not a strip of the composite followed by its layers. */
    @Test
    fun `a psb is its composite and nothing else`() {
        val file = SampleMedia.file("still.psb")
        val decoder = stillDecoderFor(file)
        val bitmap = decoder.frame(file, 0)
        assertEquals(64, bitmap.width)
        assertEquals(48, bitmap.height)
        assertTrue(decoder.frameSet(file).frames.size <= 1)
    }
}
