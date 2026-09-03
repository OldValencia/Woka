package com.woka.integration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.TransferKind
import com.woka.feature.editor.save.encodeSdrAvif
import com.woka.feature.viewer.image.readStillColor
import com.woka.feature.viewer.image.stillDecoderFor
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first encoder in this repository that is not Skia (ME-181).
 *
 * **A four-quadrant source, not a flat fill.** A flat colour survives a sheared write,
 * a channel swap and a vertical flip without any of them showing — and every one of
 * those is a live risk here, because the frame is filled by stride against a buffer the
 * encoder pads. Four different colours in four known corners fail loudly instead.
 */
class FfmpegStillEncoderTest {

    /**
     * **96×64 on purpose.** This is the width that exposed ME-182: ffmpeg pads a
     * 96-wide BGR row from 288 bytes to 384, `384 / 96` is exactly 4, and the decoder
     * used to infer four channels from three-channel data. Any other width would let
     * that bug back in unnoticed.
     */
    private val width = 96
    private val height = 64

    /** Deliberately asymmetric in every channel, so a BGR/RGB swap cannot pass. */
    private val topLeft = Triple(200, 40, 40)
    private val topRight = Triple(40, 200, 40)
    private val bottomLeft = Triple(40, 40, 200)
    private val bottomRight = Triple(220, 220, 40)

    /** AV1 at quality 20 over a 4:2:0 plane; edges cost more than flats. */
    private val tolerance = 12

    @Test
    fun `a picture survives the round trip, in the right corners`() {
        val bytes = encodeSdrAvif(quadrants())
        assertTrue(bytes.isNotEmpty(), "the encoder produced nothing")

        val file = File.createTempFile("encoder-test", ".avif")
        try {
            file.writeBytes(bytes)
            val decoded = stillDecoderFor(file).frame(file, 0)

            assertEquals(width, decoded.width)
            assertEquals(height, decoded.height)

            val pixels = decoded.toPixelMap()
            fun at(x: Int, y: Int): Triple<Int, Int, Int> {
                val c = pixels[x, y]
                return Triple(
                    (c.red * 255f).toInt(),
                    (c.green * 255f).toInt(),
                    (c.blue * 255f).toInt()
                )
            }
            assertNear(topLeft, at(width / 4, height / 4), "top-left")
            assertNear(topRight, at(width * 3 / 4, height / 4), "top-right")
            assertNear(bottomLeft, at(width / 4, height * 3 / 4), "bottom-left")
            assertNear(bottomRight, at(width * 3 / 4, height * 3 / 4), "bottom-right")
        } finally {
            file.delete()
        }
    }

    @Test
    fun `what it writes is tagged SDR`() {
        // The tag is the half of `To SDR` a person cannot see. A file whose pixels were
        // baked but whose header still claimed PQ would be tone mapped a second time by
        // the next reader — including by Woka.
        val file = File.createTempFile("encoder-tags", ".avif")
        try {
            file.writeBytes(encodeSdrAvif(quadrants()))
            val colour = readStillColor(file)
            assertEquals(TransferKind.SDR, colour.transfer)
            assertTrue(!colour.isWideGamut, "an SDR bake came out tagged BT.2020")
        } finally {
            file.delete()
        }
    }

    private fun assertNear(want: Triple<Int, Int, Int>, got: Triple<Int, Int, Int>, where: String) {
        val close = abs(want.first - got.first) <= tolerance &&
            abs(want.second - got.second) <= tolerance &&
            abs(want.third - got.third) <= tolerance
        assertTrue(close, "$where: wanted $want, got $got")
    }

    private fun quadrants(): ImageBitmap =
        Surface.makeRasterN32Premul(width, height).use { surface ->
            fun fill(x: Int, y: Int, rgb: Triple<Int, Int, Int>) {
                surface.canvas.save()
                surface.canvas.clipRect(
                    Rect.makeXYWH(x.toFloat(), y.toFloat(), width / 2f, height / 2f)
                )
                surface.canvas.clear(
                    0xFF000000.toInt() or
                        (rgb.first shl 16) or (rgb.second shl 8) or rgb.third
                )
                surface.canvas.restore()
            }
            fill(0, 0, topLeft)
            fill(width / 2, 0, topRight)
            fill(0, height / 2, bottomLeft)
            fill(width / 2, height / 2, bottomRight)
            surface.makeImageSnapshot().toComposeImageBitmap()
        }
}
