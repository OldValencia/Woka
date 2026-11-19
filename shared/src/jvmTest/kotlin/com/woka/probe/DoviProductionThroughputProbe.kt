package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.test.Test

/** Production grabber, including row dispatch and resize; bitmap ownership bounded per frame. */
class DoviProductionThroughputProbe {
    @Test
    fun `measure the production P5 path with decoder thread counts and output sizes`() {
        assumeTrue(System.getProperty("woka.probe.dv5") == "true")
        val report = mutableListOf<String>()
        val widths = System.getProperty("woka.probe.dv5.widths", "3840,1920,1280,480").split(',').map(String::toInt)
        val threadCounts = System.getProperty("woka.probe.dv5.threads", "2,4,8").split(',').map(String::toInt)
        for (width in widths) {
            for (threads in threadCounts) {
                val result = measure("Mystery Box Dolby Vision Profile 5.mp4", width, threads)
                println("DV5-PRODUCTION $result")
                report += result
            }
        }
        report += measure("Test Jellyfin 1080p DV P5.mp4", 1920, 4)
        println("DV5-PRODUCTION ${report.last()}")
        File("build/dv-p5").mkdirs()
        File("build/dv-p5/production-throughput.txt").writeText(report.joinToString("\n"))
    }

    private fun measure(name: String, width: Int, threads: Int): String {
        FreshFrameGrabber(File("D:/Movies/DV samples", name)).use { grabber ->
            grabber.pixelFormat = AV_PIX_FMT_RGBA
            grabber.imageWidth = width
            grabber.imageHeight = width * 9 / 16
            grabber.audioStream = Int.MAX_VALUE
            grabber.setVideoOption("threads", threads.toString())
            grabber.start()
            val durations = mutableListOf<Long>()
            var decode = 0L
            var copy = 0L
            var checksum = 0L
            val count = System.getProperty("woka.probe.dv5.frames", "150").toInt()
            val warmup = System.getProperty("woka.probe.dv5.warmup", "30").toInt()
            repeat(count) { index ->
                val begin = System.nanoTime()
                val frame = checkNotNull(grabber.grabImage())
                val decoded = System.nanoTime()
                val buffer = frame.image[0] as ByteBuffer
                val bytes = ByteArray(frame.imageStride * frame.imageHeight)
                buffer.rewind()
                buffer.get(bytes)
                val info = ImageInfo(frame.imageWidth, frame.imageHeight, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
                Bitmap().use { bitmap ->
                    check(bitmap.installPixels(info, bytes, frame.imageStride))
                    bitmap.setImmutable()
                }
                checksum += bytes[bytes.size / 2].toInt() and 255
                val end = System.nanoTime()
                if (index >= warmup) {
                    decode += decoded - begin
                    copy += end - decoded
                    durations += end - begin
                }
            }
            durations.sort()
            val measured = durations.size
            return String.format(Locale.ROOT,
                "%s output=%dx%d decodeThreads=%d grabMs=%.3f bitmapMs=%.3f totalMs=%.3f fps=%.2f p99Ms=%.3f checksum=%d",
                name, width, grabber.imageHeight, threads, decode / (measured * 1e6), copy / (measured * 1e6),
                (decode + copy) / (measured * 1e6), measured * 1e9 / (decode + copy),
                durations[(measured * 0.99).toInt().coerceAtMost(measured - 1)] / 1e6, checksum)
        }
    }
}
