package com.woka.probe

import com.sun.management.OperatingSystemMXBean
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.junit.Assume.assumeTrue
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import kotlin.test.Test

/** TEMPORARY (EK-001): GPU decode plus bounded threaded conversion and actual bitmap staging. */
class EightKAcceleratedProbe {
    private val type = FFmpegFrameGrabber::class.java
    private val picture = type.getDeclaredField("picture").apply { isAccessible = true }

    @Test
    fun `measure accelerated frame preparation before considering player integration`() {
        assumeTrue("Opt in with woka.probe.8k", System.getProperty("woka.probe.8k") == "true")
        val root = File("build/dv-investigation/accelerated").also { it.mkdirs() }
        val report = File(root, "throughput.txt")
        report.writeText("EK-001; 60 warmup + 300 frames; bitmap closed immediately; no UI\n")
        for (kind in listOf("10bit", "HDR10")) {
            val file = File("D:/Movies/DV samples/Test Jellyfin 8K HEVC $kind 150M.mp4")
            for (width in listOf(1440, 2560)) {
                for (threads in listOf(1, 2, 4)) {
                    val row = trial(file, width, threads)
                    report.appendText("$row\n")
                    println("EK001 $row")
                }
            }
        }
    }

    private fun trial(file: File, width: Int, threads: Int): String {
        val grabber = HardwareContextGrabber(file, "cuda-native")
        grabber.imageMode = FrameGrabber.ImageMode.RAW
        grabber.audioStream = Int.MAX_VALUE
        grabber.setVideoOption("threads", "4")
        try {
            grabber.start()
            ThreadedRgbaProbe(width, width * 9 / 16, threads).use { converter ->
                fun next(): Frame {
                    checkNotNull(grabber.grabFrame(false, true, false, false, false))
                    return converter.convert(picture.get(grabber) as AVFrame)
                }
                repeat(60) { bitmap(next()) }
                val times = DoubleArray(300)
                val os = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
                val cpuStart = os.processCpuTime
                val start = System.nanoTime()
                repeat(300) { index ->
                    val began = System.nanoTime()
                    bitmap(next())
                    times[index] = (System.nanoTime() - began) / 1e6
                }
                val elapsed = System.nanoTime() - start
                return "${file.name} width=$width threads=$threads fps=${number(300e9 / elapsed)} " +
                    "meanMs=${number(times.average())} p95Ms=${number(percentile(times.sorted(), .95))} " +
                    "cpuPercent=${number(100.0 * (os.processCpuTime - cpuStart) / elapsed / os.availableProcessors)} " +
                    "physicalMiB=${Pointer.physicalBytes() / 1048576} deviceVramMiB=${gpuMb()}"
            }
        } finally {
            try { grabber.stop() } finally { grabber.release() }
        }
    }

    private fun bitmap(frame: Frame) {
        val pixels = ByteArray(frame.imageStride * frame.imageHeight)
        (frame.image[0] as ByteBuffer).duplicate().get(pixels)
        val info = ImageInfo(frame.imageWidth, frame.imageHeight, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        Bitmap().use {
            check(it.installPixels(info, pixels, frame.imageStride))
            it.setImmutable()
        }
    }
}
