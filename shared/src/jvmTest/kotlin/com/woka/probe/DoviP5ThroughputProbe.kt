package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.ThreadedVideoConverter
import org.bytedeco.javacv.FrameGrabber
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.test.Test

/** DV5-101: decode + native colour + optional resize + bitmap copy, no UI/GPU draw. */
class DoviP5ThroughputProbe {
    @Test
    fun `measure P5 conversion with software decoding and bitmap ownership`() {
        assumeTrue(System.getProperty("woka.probe.dv5") == "true")
        val library = File(System.getProperty("woka.probe.dv5.library")).resolveSibling("woka_p5_probe.dll")
        val samples = listOf("Mystery Box Dolby Vision Profile 5.mp4", "Test Jellyfin 1080p DV P5.mp4")
        val report = mutableListOf<String>()
        for (sample in samples) {
            for (threads in listOf(0, 4, 8)) {
                val result = measure(File("D:/Movies/DV samples", sample), library, threads)
                report += result
                println("DV5-THROUGHPUT $result")
            }
            if (sample.startsWith("Mystery")) {
                val result = measure(File("D:/Movies/DV samples", sample), library, 8, 1920)
                report += result
                println("DV5-THROUGHPUT $result")
            }
        }
        File("build/dv-p5").mkdirs()
        File("build/dv-p5/throughput.txt").writeText(report.joinToString("\n"))
    }

    private fun measure(file: File, library: File, threads: Int, drawWidth: Int = 0): String {
        val grabber = FreshFrameGrabber(file, allowUnsupportedColour = true)
        var converter: DoviColourAccess? = null
        var scaler: ThreadedVideoConverter? = null
        try {
            grabber.imageMode = FrameGrabber.ImageMode.RAW
            grabber.audioStream = Int.MAX_VALUE
            grabber.setVideoOption("threads", "4")
            grabber.start()
            val width = grabber.imageWidth
            val height = grabber.imageHeight
            val outputWidth = drawWidth.takeIf { it > 0 } ?: width
            val outputHeight = height * outputWidth / width
            val info = ImageInfo(outputWidth, outputHeight, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
            if (threads != 0) converter = DoviColourAccess(library.toPath(), width, height, threads)
            if (outputWidth != width) scaler = ThreadedVideoConverter(outputWidth, outputHeight)
            var decodeNs = 0L
            var convertNs = 0L
            var copyNs = 0L
            var maxFrameNs = 0L
            var checksum = 0L
            val durations = mutableListOf<Long>()
            repeat(150) { iteration ->
                val begin = System.nanoTime()
                checkNotNull(grabber.grabImage()) { "EOF at frame $iteration" }
                val decoded = System.nanoTime()
                val picture = checkNotNull(grabber.lastPicture)
                converter?.convert(picture)
                val converted = System.nanoTime()
                converter?.let { access ->
                    val scaled = scaler?.convert(access.outputPicture, grabber.timestamp)
                    val stride = scaled?.imageStride ?: width * 4
                    val bytes = if (scaled == null) access.copyPixels() else {
                        val buffer = scaled.image[0] as ByteBuffer
                        ByteArray(stride * outputHeight).also {
                            buffer.rewind()
                            buffer.get(it)
                        }
                    }
                    checksum += bytes[bytes.size / 2].toInt() and 255
                    Bitmap().use { bitmap ->
                        check(bitmap.installPixels(info, bytes, stride))
                        bitmap.setImmutable()
                    }
                }
                val copied = System.nanoTime()
                if (iteration >= 30) {
                    decodeNs += decoded - begin
                    convertNs += converted - decoded
                    copyNs += copied - converted
                    maxFrameNs = maxOf(maxFrameNs, copied - begin)
                    durations += copied - begin
                }
            }
            durations.sort()
            val total = decodeNs + convertNs + copyNs
            return String.format(Locale.ROOT,
                "%s %dx%d output=%dx%d workers=%d frames=120 decodeMs=%.3f convertMs=%.3f resizeCopyMs=%.3f " +
                    "totalMs=%.3f fps=%.2f p99Ms=%.3f maxMs=%.3f checksum=%d",
                file.name, width, height, outputWidth, outputHeight, threads,
                decodeNs / 120e6, convertNs / 120e6, copyNs / 120e6,
                total / 120e6, 120e9 / total, durations[118] / 1e6, maxFrameNs / 1e6, checksum)
        } finally {
            try {
                try {
                    scaler?.close()
                } finally {
                    converter?.close()
                }
            } finally {
                try {
                    grabber.stop()
                } finally {
                    grabber.release()
                }
            }
        }
    }
}
