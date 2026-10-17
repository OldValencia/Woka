package com.woka.integration

import com.woka.core.data.MediaKind
import com.woka.core.data.needsWokaCover
import com.woka.core.data.mediaKind
import com.woka.feature.viewer.image.FfmpegStillDecoder
import com.woka.feature.viewer.image.ImageIoStillDecoder
import com.woka.feature.viewer.image.SkiaFrameDecoder
import com.woka.feature.viewer.image.SvgStillDecoder
import com.woka.feature.viewer.image.StillDecoder
import com.woka.feature.viewer.image.stillDecoderFor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Routing is by name only — nothing here opens a file. */
private fun named(name: String) = File("routing-fixture", name)

/**
 * Which library reads which format.
 *
 * `MediaKind` decides what the app lists and `StillDecoders` decides what reads
 * it, and the two can silently disagree: an extension added to one and forgotten
 * in the other falls through to skia, which answers `Unsupported format` and
 * looks like a corrupt file. This is the test that stops that.
 */
class StillDecoderRoutingTest {

    private val expected: Map<String, StillDecoder> = mapOf(
        "heic" to FfmpegStillDecoder,
        "heif" to FfmpegStillDecoder,
        "avif" to FfmpegStillDecoder,
        "tif" to ImageIoStillDecoder,
        "tiff" to ImageIoStillDecoder,
        "jpg" to SkiaFrameDecoder,
        "jpeg" to SkiaFrameDecoder,
        "png" to SkiaFrameDecoder,
        "bmp" to SkiaFrameDecoder,
        "gif" to SkiaFrameDecoder,
        "webp" to SkiaFrameDecoder,
        "svg" to SvgStillDecoder,
        "tga" to FfmpegStillDecoder,
        "qoi" to FfmpegStillDecoder,
        "jp2" to FfmpegStillDecoder,
        "pcx" to FfmpegStillDecoder,
        "ppm" to FfmpegStillDecoder
    )

    @Test
    fun `each extension reaches the decoder that can read it`() {
        expected.forEach { (extension, decoder) ->
            assertSame(decoder, stillDecoderFor(named("photo.$extension")), extension)
        }
    }

    @Test
    fun `every routed extension is one the app actually lists`() {
        expected.keys.forEach { extension ->
            assertEquals(MediaKind.Image, named("photo.$extension").mediaKind(), extension)
        }
    }

    @Test
    fun `the extension is matched case-insensitively`() {
        assertSame(FfmpegStillDecoder, stillDecoderFor(named("PHOTO.HEIC")))
        assertSame(ImageIoStillDecoder, stillDecoderFor(named("Scan.TIFF")))
    }

    /**
     * Animated `.webp` is skia's alone. FFmpeg opens the container and grabs
     * `0 frames, 0×0` from an animated one — measured on both the 6.1.1 and
     * 8.0.1 builds — so routing it there on the strength of the static case
     * would trade eight frames for none.
     */
    @Test
    fun `webp is not routed to FFmpeg despite FFmpeg opening a static one`() {
        assertSame(SkiaFrameDecoder, stillDecoderFor(named("anim.webp")))
    }

    /**
     * The formats only Woka's decoders can open are exactly the ones the grid
     * has to draw for itself. If the two lists drift, a tile goes grey again.
     */
    @Test
    fun `the formats only Woka can open are the ones the grid draws itself`() {
        val wokaOnly = expected.filterValues { it != SkiaFrameDecoder }.keys
        wokaOnly.forEach { extension ->
            assertTrue(named("photo.$extension").needsWokaCover, extension)
        }
    }
}
