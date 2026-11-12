package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.lastPicture
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.test.Test

/** TEMPORARY (EK-001): complete pixel and frame-property comparison after matching seeks. */
class EightKAcceleratedParityProbe {
    private val type = FFmpegFrameGrabber::class.java
    private val picture = type.getDeclaredField("picture").apply { isAccessible = true }

    @Test
    fun `compare accelerated frames against the existing software colour path`() {
        assumeTrue("Opt in with woka.probe.8k", System.getProperty("woka.probe.8k") == "true")
        val root = File("build/dv-investigation/accelerated").also { it.mkdirs() }
        val report = File(root, "parity.txt")
        report.writeText("Full RGB comparison; metadata payload hashes; matching timestamps required\n")
        for (kind in listOf("10bit", "HDR10")) {
            val file = File("D:/Movies/DV samples/Test Jellyfin 8K HEVC $kind 150M.mp4")
            for (width in listOf(1440, 2560)) compare(file, width, report)
        }
        for (name in listOf("Test Jellyfin 4K DV P8.1.mp4", "Hybrid HDR10Plus DV Sample.mkv")) {
            compare(File("D:/Movies/DV samples", name), 1440, report)
        }
    }

    private fun compare(file: File, width: Int, report: File) {
        val software = FreshFrameGrabber(file)
        val gpu = HardwareContextGrabber(file, "cuda-native")
        software.imageWidth = width
        software.imageHeight = width * 9 / 16
        software.pixelFormat = AV_PIX_FMT_RGBA
        gpu.imageMode = FrameGrabber.ImageMode.RAW
        for (grabber in listOf(software, gpu)) {
            grabber.audioStream = Int.MAX_VALUE
            grabber.setVideoOption("threads", "4")
        }
        try {
            software.start()
            gpu.start()
            ThreadedRgbaProbe(width, width * 9 / 16, 4).use { converter ->
                for (at in listOf(1_000_000L, 5_000_000L, 20_000_000L, 2_000_000L)) {
                    software.setTimestamp(at)
                    gpu.setTimestamp(at)
                    val reference = checkNotNull(software.grabImage())
                    val expected = bytes(reference)
                    val metadata = properties(checkNotNull(software.lastPicture))
                    val decoded = checkNotNull(gpu.grabFrame(false, true, false, false, false))
                    val actual = converter.convert(picture.get(gpu) as AVFrame)
                    val received = bytes(actual)
                    var sum = 0L
                    var maximum = 0
                    var changed = 0
                    for (index in expected.indices) {
                        if (index % 4 == 3) continue
                        val delta = abs((expected[index].toInt() and 255) - (received[index].toInt() and 255))
                        sum += delta
                        maximum = maxOf(maximum, delta)
                        if (delta != 0) changed++
                    }
                    val row = "${file.name} width=$width targetUs=$at " +
                        "sameTimestamp=${reference.timestamp == decoded.timestamp} tsUs=${reference.timestamp} " +
                        "maxRgbDiff=$maximum meanRgbDiff=${number(sum.toDouble() / (expected.size / 4 * 3))} " +
                        "changedChannels=$changed metadataEqual=${metadata == properties(converter.output)} " +
                        "metadata=$metadata"
                    report.appendText("$row\n")
                    println("EK001 $row")
                }
            }
        } finally {
            for (grabber in listOf(gpu, software)) {
                try { grabber.stop() } finally { grabber.release() }
            }
        }
    }

    private fun bytes(frame: Frame): ByteArray {
        val rowBytes = frame.imageWidth * 4
        val data = ByteArray(rowBytes * frame.imageHeight)
        val buffer = (frame.image[0] as ByteBuffer).duplicate()
        repeat(frame.imageHeight) { y ->
            buffer.position(y * frame.imageStride)
            buffer.get(data, y * rowBytes, rowBytes)
        }
        return data
    }

    private fun properties(frame: AVFrame): String {
        val side = (0 until frame.nb_side_data()).map { index ->
            val data = frame.side_data(index)
            val hash = CRC32()
            hash.update(data.data().capacity(data.size().toLong()).asByteBuffer())
            "${data.type()}:${data.size()}:${hash.value}"
        }.sorted()
        return "${frame.color_trc()}/${frame.color_primaries()}/${frame.colorspace()}/${frame.color_range()}:$side"
    }
}
