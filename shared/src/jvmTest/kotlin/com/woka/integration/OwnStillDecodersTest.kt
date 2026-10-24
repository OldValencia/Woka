package com.woka.integration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.SampleMedia
import org.jetbrains.skia.Image
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The decoders Woka wrote itself — `.paint`, `.hdr`, `.ai`, and the `.jxr` shim —
 * held to an outside reference rather than to their own idea of right.
 *
 * The PNGs under `reference/`: for `.paint`, ImageMagick 7.1.2's decode — libheif's
 * overlay composite. For `.hdr`, the **sRGB source** ImageMagick wrote the file
 * from: ImageMagick's own `.hdr` read stays linear (`0B6803` where the source is
 * `3BAB1F`), so the round trip is the only reference that means "looks right".
 */
class OwnStillDecodersTest {

    @Test
    fun `a paint project matches libheif's composite`() {
        listOf("still.paint", "multilayer.paint").forEach { name ->
            assertMatchesReference(name, "reference/$name.png", tolerance = 1)
        }
    }

    @Test
    fun `a paint project reports the canvas size without decoding the layers`() {
        val file = SampleMedia.file("multilayer.paint")
        val size = stillDecoderFor(file).nativeSize(file, 0)
        assertEquals(1022, size.width)
        assertEquals(575, size.height)
    }

    /**
     * RGBE keeps an 8-bit mantissa per channel under a shared exponent, so a dark
     * channel beside a bright one loses low bits — measured worst case 6 levels,
     * mean 0.11, which is the format's loss and not the decoder's.
     */
    @Test
    fun `a radiance file round-trips to the sRGB picture it was written from`() {
        assertMatchesReference("still.hdr", "reference/still.hdr.png", tolerance = 6)
    }

    /**
     * A vertical gradient compresses 56:1 — what TwelveMonkeys' 16:1 guard refused.
     * A zero channel beside a full one reads 13, not 0: half a mantissa step at the
     * steep end of the sRGB curve.
     */
    @Test
    fun `a radiance file that compresses well still opens`() {
        val file = SampleMedia.file("flat.hdr")
        val pixels = pixels(stillDecoderFor(file).frame(file, 0))
        assertTrue(red(pixels.first()) > 250 && blue(pixels.first()) < 16, pixels.first().toUInt().toString(16))
        assertTrue(blue(pixels.last()) > 250 && red(pixels.last()) < 16, pixels.last().toUInt().toString(16))
    }

    /**
     * JPEG XR against Windows' own WIC decode of each sample: 8-bit, alpha, lossy,
     * 16-bit gray and half-float HDR. WIC rounds 16-bit and linear values its own
     * way, one level apart (`natives/jxr/README.md`); alpha adds one more through
     * the premultiplied bitmap both sides are read back from.
     */
    @Test
    fun `a jpeg xr file matches Windows' own decode`() {
        listOf("rgb24_gradient", "bgra32_alpha", "hdr_rgbahalf", "rgb24_lossy_q50", "gray16").forEach { sample ->
            assertMatchesReference("jxr-$sample.jxr", "reference/jxr-$sample.jxr.png", tolerance = 2)
        }
    }

    /** ImageMagick wrote a 200×100 pt page: white, with a blue rectangle inset 20 pt. */
    @Test
    fun `an illustrator file renders its first page at twice its point size`() {
        val file = SampleMedia.file("still.ai")
        val decoder = stillDecoderFor(file)
        val bitmap = decoder.frame(file, 0)
        assertEquals(400, bitmap.width)
        assertEquals(200, bitmap.height)
        assertEquals(bitmap.width, decoder.nativeSize(file, 0).width)
        val all = pixels(bitmap)
        assertEquals(0xFFFFFF, all[0] and 0xFFFFFF, "corner")
        val centre = all[100 * 400 + 200]
        assertTrue(blue(centre) > 200 && red(centre) < 100, centre.toUInt().toString(16))
    }

    private fun assertMatchesReference(name: String, reference: String, tolerance: Int) {
        val file = SampleMedia.file(name)
        val ours = pixels(stillDecoderFor(file).frame(file, 0))
        val theirs = pixels(Image.makeFromEncoded(SampleMedia.file(reference).readBytes()).toComposeImageBitmap())
        assertEquals(theirs.size, ours.size, "$name size")
        var worst = 0
        for (i in ours.indices) {
            for (shift in intArrayOf(0, 8, 16, 24)) {
                worst = maxOf(worst, abs((ours[i] shr shift and 0xFF) - (theirs[i] shr shift and 0xFF)))
            }
        }
        assertTrue(worst <= tolerance, "$name differs from its reference by up to $worst")
    }

    private fun pixels(bitmap: ImageBitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also { bitmap.readPixels(it) }

    private fun red(argb: Int): Int = (argb shr 16) and 0xFF

    private fun blue(argb: Int): Int = argb and 0xFF
}
