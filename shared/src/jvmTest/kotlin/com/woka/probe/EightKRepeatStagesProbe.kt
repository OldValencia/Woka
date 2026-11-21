package com.woka.probe

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.av_frame_remove_side_data
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.junit.Assume.assumeTrue
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import kotlin.test.Test

/**
 * TEMPORARY (8K investigation §7): repeated longer runs with separate conversion/copy timings.
 * Each bitmap is closed immediately; this measures throughput, not UI retention or GPU upload.
 * 60 warm-up + 300 timed frames per trial replace the earlier 8 + 48-frame snapshot.
 */
class EightKRepeatStagesProbe {
    @Test
    fun `repeat software decode conversion and bitmap timing`() {
        assumeTrue("Opt in with woka.probe.8k", System.getProperty("woka.probe.8k") == "true")
        val output = File("build/dv-investigation/live").also { it.mkdirs() }
        val report = File(output, "stages.txt")
        report.writeText("JVM ${ManagementFactory.getRuntimeMXBean().inputArguments}\n")
        repeat(2) { round ->
            for (kind in listOf("10bit", "HDR10")) {
                for (width in listOf(0, 1440, 2560)) {
                    val file = File("D:/Movies/DV samples/Test Jellyfin 8K HEVC $kind 150M.mp4")
                    val row = trial(file, width, round + 1)
                    report.appendText("$row\n")
                    println("DV8K $row")
                }
            }
        }
    }

    private fun trial(file: File, width: Int, round: Int): String {
        val grabber = openHardwareLane(file, "sw", width)
        try {
            val type = FFmpegFrameGrabber::class.java
            val process = type.getDeclaredMethod("processImage").apply { isAccessible = true }
            val rgbField = type.getDeclaredField("picture_rgb").apply { isAccessible = true }
            repeat(60) { checkNotNull(grabber.grabFrame(false, true, width > 0, false, false)) }
            val times = Array(4) { DoubleArray(300) }
            val gc = ManagementFactory.getGarbageCollectorMXBeans()
            val startGc = gc.sumOf { it.collectionTime }
            repeat(300) { index ->
                val start = System.nanoTime()
                val frame = checkNotNull(grabber.grabFrame(false, true, false, false, false))
                val decoded = System.nanoTime()
                if (width > 0) {
                    val rgb = rgbField.get(grabber) as AVFrame
                    while (rgb.nb_side_data() > 0) av_frame_remove_side_data(rgb, rgb.side_data(0).type())
                    process.invoke(grabber)
                }
                val converted = System.nanoTime()
                if (width > 0) bitmap(frame)
                val end = System.nanoTime()
                times[0][index] = (decoded - start) / 1e6
                times[1][index] = (converted - decoded) / 1e6
                times[2][index] = (end - converted) / 1e6
                times[3][index] = (end - start) / 1e6
            }
            val names = listOf("grabWait", "swscale", "bitmap", "total")
            val summary = times.mapIndexed { index, values ->
                "${names[index]}MeanMs=${number(values.average())} " +
                    "${names[index]}P95Ms=${number(percentile(values.sorted(), .95))}"
            }.joinToString(" ")
            return "${file.name} round=$round width=$width pixels=${width > 0} frames=300 $summary " +
                "fps=${number(1000 / times[3].average())} gcMs=${gc.sumOf { it.collectionTime } - startGc}"
        } finally {
            try { grabber.stop() } finally { grabber.release() }
        }
    }

    private fun bitmap(frame: Frame) {
        val staging = ByteArray(frame.imageStride * frame.imageHeight)
        val source = frame.image[0] as ByteBuffer
        source.rewind()
        source.get(staging)
        source.rewind()
        val info = ImageInfo(frame.imageWidth, frame.imageHeight, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        Bitmap().use {
            check(it.installPixels(info, staging, frame.imageStride))
            it.setImmutable()
        }
    }
}
