package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.DoviColourConverter
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.ThreadedVideoConverter
import com.woka.feature.viewer.video.engine.sync.grabImageAt
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber.ImageMode
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test

class DoviGpuProbe {
    @Test
    fun `compare GPU reconstruction with the verified CPU path`() {
        assumeTrue(System.getProperty("woka.probe.dv5") == "true")
        val library = File(System.getProperty("woka.probe.dv5.library")).toPath()
        val report = mutableListOf<String>()
        for (name in listOf("Mystery Box Dolby Vision Profile 5.mp4", "Test Jellyfin 1080p DV P5.mp4")) {
            FreshFrameGrabber(File("D:/Movies/DV samples", name), allowUnsupportedColour = true).use { grabber ->
                grabber.imageMode = ImageMode.RAW
                grabber.setVideoOption("threads", "4")
                grabber.start()
                for (width in listOf(grabber.imageWidth, grabber.imageWidth / 2, maxOf(400, grabber.imageWidth / 8))) {
                    DoviGpuAccess(library, width, grabber.imageHeight * width / grabber.imageWidth).use { gpu ->
                        DoviColourConverter(preferGpu = false).use { cpu ->
                            ThreadedVideoConverter(gpu.width, gpu.height).use { scaler ->
                                for (second in listOf(1, 5, 10)) {
                                    val raw = checkNotNull(grabber.grabImageAt(second * 1_000_000L))
                                    val status = gpu.convert(raw.opaque as AVFrame)
                                    if (status != 0) {
                                        report += "unavailable=$status file=$name width=$width"
                                        println("DV5-GPU ${report.last()}")
                                        continue
                                    }
                                    val full = cpu.convert(raw)
                                    val expected = if (width == grabber.imageWidth) full
                                    else scaler.convert(full.opaque as AVFrame, raw.timestamp)
                                    val difference = compare(expected, gpu)
                                    val begin = System.nanoTime()
                                    repeat(60) { check(gpu.convert(raw.opaque as AVFrame) == 0) }
                                    val ms = (System.nanoTime() - begin) / 60e6
                                    report += "$name width=$width second=$second $difference gpuMs=$ms"
                                    println("DV5-GPU ${report.last()}")
                                }
                            }
                        }
                    }
                }
            }
        }
        File("build/dv-p5/gpu-comparison.txt").writeText(report.joinToString("\n"))
    }

    private fun compare(expected: Frame, gpu: DoviGpuAccess): String {
        val a = expected.image[0] as ByteBuffer
        val b = gpu.bytes()
        var max = 0
        var total = 0L
        var over = 0L
        for (y in 0 until gpu.height) for (x in 0 until gpu.width * 4) {
            val diff = abs((a.get(y * expected.imageStride + x).toInt() and 255) -
                (b[y * gpu.width * 4 + x].toInt() and 255))
            max = maxOf(max, diff)
            total += diff
            if (diff > 1) over++
        }
        return "max=$max mean=${total.toDouble() / b.size} over1=$over"
    }
}
