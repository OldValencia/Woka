package com.woka.integration

import com.woka.feature.viewer.video.engine.decode.DoviColourConverter
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AVCHROMA_LOC_CENTER
import org.bytedeco.ffmpeg.global.avutil.AVCHROMA_LOC_LEFT
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber.ImageMode
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DoviGpuColourTest {
    @Test
    fun `GPU reconstruction and resize match CPU colour and an independent triangle filter`() = onRaw { raw ->
        DoviColourConverter(preferGpu = false).use { cpu ->
            val reference = cpu.convert(raw)
            DoviColourConverter().use { gpu ->
                val available = gpu.convert(raw, 400, 226)
                assumeTrue("A supported D3D11 GPU is required", available.imageWidth == 400)
                val full = gpu.convert(raw)
                val a = reference.image[0] as ByteBuffer
                val b = full.image[0] as ByteBuffer
                var worst = 0
                for (y in 0 until full.imageHeight) for (x in 0 until full.imageWidth * 4) {
                    worst = maxOf(worst, abs((a.get(y * reference.imageStride + x).toInt() and 255) -
                        (b.get(y * full.imageStride + x).toInt() and 255)))
                }
                assertTrue(worst <= 1, "Full resolution colour error: $worst")
                for ((width, height) in listOf(960 to 540, 400 to 226, 1919 to 1079, 2048 to 1152)) {
                    val scaled = gpu.convert(raw, width, height)
                    val pixels = scaled.image[0] as ByteBuffer
                    val xs = listOf(0, 1, width - 2, width - 1) + (0 until width step 31)
                    val ys = listOf(0, 1, height - 2, height - 1) + (0 until height step 37)
                    for (y in ys) for (x in xs) {
                        for (c in 0..2) {
                            val expected = triangle(reference, width, height, x, y, c)
                            val actual = pixels.get(y * scaled.imageStride + x * 4 + c).toInt() and 255
                            assertTrue(abs(actual - expected) <= 1, "$width x $height at $x,$y channel $c: $actual vs $expected")
                        }
                        assertEquals(255, pixels.get(y * scaled.imageStride + x * 4 + 3).toInt() and 255)
                    }
                }
            }
        }
    }

    @Test
    fun `unsupported GPU chroma uses CPU without reusing a previous resized output`() = onRaw { raw ->
        DoviColourConverter().use { converter ->
            converter.convert(raw, 400, 226)
            val picture = raw.opaque as AVFrame
            picture.chroma_location(AVCHROMA_LOC_CENTER)
            val fallback = converter.convert(raw, 400, 226)
            assertEquals(raw.imageWidth, fallback.imageWidth)
            DoviColourConverter(preferGpu = false).use { cpu ->
                val reference = cpu.convert(raw)
                val a = fallback.image[0] as ByteBuffer
                val b = reference.image[0] as ByteBuffer
                for (y in 0 until fallback.imageHeight step 23) for (x in 0 until fallback.imageWidth * 4 step 29) {
                    assertEquals(b.get(y * reference.imageStride + x), a.get(y * fallback.imageStride + x))
                }
            }
            picture.chroma_location(AVCHROMA_LOC_LEFT)
            assertEquals(raw.imageWidth, converter.convert(raw, 400, 226).imageWidth)
        }
    }

    private fun triangle(frame: Frame, width: Int, height: Int, x: Int, y: Int, channel: Int): Int {
        val sx = frame.imageWidth.toDouble() / width
        val sy = frame.imageHeight.toDouble() / height
        val cx = (x + 0.5) * sx - 0.5
        val cy = (y + 0.5) * sy - 0.5
        val rx = max(sx, 1.0)
        val ry = max(sy, 1.0)
        val pixels = frame.image[0] as ByteBuffer
        var sum = 0.0
        var weights = 0.0
        for (iy in ceil(cy - ry).toInt()..floor(cy + ry).toInt()) {
            for (ix in ceil(cx - rx).toInt()..floor(cx + rx).toInt()) {
                val weight = max(0.0, 1.0 - abs(ix - cx) / rx) * max(0.0, 1.0 - abs(iy - cy) / ry)
                val offset = iy.coerceIn(0, frame.imageHeight - 1) * frame.imageStride + ix.coerceIn(0, frame.imageWidth - 1) * 4
                sum += weight * (pixels.get(offset + channel).toInt() and 255)
                weights += weight
            }
        }
        return (sum / weights + 0.5).toInt()
    }

    private fun onRaw(block: (Frame) -> Unit) {
        val file = File("D:/Movies/DV samples/Test Jellyfin 1080p DV P5.mp4")
        assumeTrue("The author's P5 sample is required", file.isFile)
        FreshFrameGrabber(file, allowUnsupportedColour = true).use { grabber ->
            grabber.imageMode = ImageMode.RAW
            grabber.start()
            block(assertNotNull(grabber.grabImageAt(5_000_000L)))
        }
    }
}
