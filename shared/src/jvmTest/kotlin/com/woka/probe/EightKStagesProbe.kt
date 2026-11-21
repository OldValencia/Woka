package com.woka.probe

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.av_frame_remove_side_data
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test

/**
 * TEMPORARY (1.0.8 8K/150M investigation): separate decode, swscale and bitmap preparation.
 * Bitmap staging mirrors `FramePacer.convert`, but closes each bitmap immediately because this
 * is a throughput probe, not a retention/UI test. GPU upload and window drawing are not timed.
 */
class EightKStagesProbe {
    @Test
    fun `locate the 8K playback budget`() {
        val root = File("build/dv-investigation").absoluteFile.also { it.mkdirs() }
        val rows = mutableListOf<String>()
        for (name in listOf("Test Jellyfin 8K HEVC 10bit 150M.mp4", "Test Jellyfin 8K HEVC HDR10 150M.mp4")) {
            for (mode in listOf("sw", "cuda-native")) {
                trial(name, mode, 0, false, rows)
                for (width in listOf(0, 3840, 1920)) trial(name, mode, width, true, rows)
            }
        }
        File(root, "8k-stages.txt").writeText(rows.joinToString("\n"))
        rows.forEach { println("DV8K $it") }
    }

    private fun trial(name: String, mode: String, width: Int, pixels: Boolean, rows: MutableList<String>) {
        var grabber: FFmpegFrameGrabber? = null
        try {
            grabber = openHardwareLane(File("D:/Movies/DV samples", name), mode, width)
            val type = FFmpegFrameGrabber::class.java
            val process = type.getDeclaredMethod("processImage").apply { isAccessible = true }
            val rgbField = type.getDeclaredField("picture_rgb").apply { isAccessible = true }
            repeat(8) { grabber.grabFrame(false, true, pixels, false, false) ?: error("Short warmup") }
            var decodeNs = 0L
            var convertNs = 0L
            var bitmapNs = 0L
            var totalNs = 0L
            repeat(48) {
                val began = System.nanoTime()
                val frame = grabber.grabFrame(false, true, pixels && mode != "sw", false, false) ?: error("Short sample")
                val decoded = System.nanoTime()
                if (pixels && mode == "sw") {
                    val rgb = rgbField.get(grabber) as AVFrame
                    while (rgb.nb_side_data() > 0) av_frame_remove_side_data(rgb, rgb.side_data(0).type())
                    process.invoke(grabber)
                }
                val converted = System.nanoTime()
                if (pixels) prepareBitmap(frame)
                val ended = System.nanoTime()
                decodeNs += decoded - began
                convertNs += converted - decoded
                bitmapNs += ended - converted
                totalNs += ended - began
            }
            val scale = 48 * 1_000_000.0
            rows += "$name mode=$mode width=$width pixels=$pixels codec=${hardwareCodec(grabber)} " +
                "decodeOrGpuPipelineMs=${"%.2f".format(decodeNs / scale)} " +
                "swscaleMs=${"%.2f".format(convertNs / scale)} bitmapMs=${"%.2f".format(bitmapNs / scale)} " +
                "totalMs=${"%.2f".format(totalNs / scale)} fps=${"%.1f".format(48e9 / totalNs)}"
        } catch (failure: Throwable) {
            rows += "$name mode=$mode width=$width pixels=$pixels FAILED ${failure.message}"
        } finally {
            runCatching { grabber?.stop() }
            runCatching { grabber?.release() }
            System.gc()
        }
    }

    private fun prepareBitmap(frame: Frame) {
        val count = frame.imageStride * frame.imageHeight
        val staging = ByteArray(count)
        val buffer = frame.image[0] as ByteBuffer
        buffer.rewind()
        buffer.get(staging)
        buffer.rewind()
        val info = ImageInfo(frame.imageWidth, frame.imageHeight, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        Bitmap().use { bitmap ->
            check(bitmap.installPixels(info, staging, frame.imageStride))
            bitmap.setImmutable()
        }
    }
}
