package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.AcceleratedFrameGrabber
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.Frame
import org.bytedeco.javacpp.Pointer
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.test.Test

/** TEMPORARY (EK-002): public-API adapter seek, delayed EOF, restart and software handoff evidence. */
class EightKAdapterProbe {
    @Test
    fun `compare production adapter through seek EOF restart and fallback`() {
        assumeTrue("Opt in with woka.probe.8k", System.getProperty("woka.probe.8k") == "true")
        val report = File("build/dv-investigation/accelerated/adapter.txt")
        report.parentFile.mkdirs()
        report.writeText("Production adapter; timestamps and full pixel CRC; repeated release\n")
        for (kind in listOf("10bit", "HDR10")) {
            val file = File("D:/Movies/DV samples/Test Jellyfin 8K HEVC $kind 150M.mp4")
            for (width in listOf(1440, 2560)) compare(file, width, report)
        }
    }

    private fun compare(file: File, width: Int, report: File) {
        val software = FreshFrameGrabber(file)
        val gpu = AcceleratedFrameGrabber(file)
        for (grabber in listOf(software, gpu)) {
            grabber.imageWidth = width
            grabber.imageHeight = width * 9 / 16
            grabber.pixelFormat = AV_PIX_FMT_RGBA
            grabber.audioStream = Int.MAX_VALUE
            grabber.setVideoOption("threads", "4")
        }
        fun log(value: String) {
            val row = "${file.name} width=$width $value"
            report.appendText("$row\n")
            println("EK002 $row")
        }
        try {
            software.start()
            gpu.start()
            log("repeatedStartRejected=${runCatching { gpu.start() }.isFailure}")
            log("hardware=${gpu.hardwareActive} failure=${gpu.accelerationFailure}")
            for (at in listOf(0L, 5_000_000L, 20_000_000L, 2_000_000L, 29_800_000L)) {
                software.setTimestamp(at)
                gpu.setTimestamp(at)
                var equal = true
                var count = 0
                var eofEqual = true
                repeat(40) {
                    val expected = software.grabImage()
                    val actual = gpu.grabImage()
                    eofEqual = eofEqual && (expected == null) == (actual == null)
                    if (expected != null && actual != null) {
                        equal = equal && expected.timestamp == actual.timestamp && hash(expected) == hash(actual)
                        count++
                    }
                }
                log("targetUs=$at frames=$count equal=$equal eofEqual=$eofEqual hardware=${gpu.hardwareActive}")
            }
            repeat(3) { cycle ->
                software.restart()
                gpu.restart()
                val expected = checkNotNull(software.grabImage())
                val actual = checkNotNull(gpu.grabImage())
                log("restart=$cycle equal=${expected.timestamp == actual.timestamp && hash(expected) == hash(actual)} " +
                    "hardware=${gpu.hardwareActive} physicalMiB=${Pointer.physicalBytes() / 1048576}")
            }
            gpu.setTimestamp(2_000_000L)
            gpu.grabImage()
            val next = gpu.timestamp + 1
            software.setTimestamp(next)
            val expected = checkNotNull(software.grabImage())
            // The data-grab contract is deliberately unsupported by the video-only adapter.
            val actual = checkNotNull(gpu.grabFrame(false, true, true, false, true))
            log("fallback hardware=${gpu.hardwareActive} failure=${gpu.accelerationFailure} " +
                "equal=${expected.timestamp == actual.timestamp && hash(expected) == hash(actual)}")
        } finally {
            gpu.stop()
            gpu.release()
            software.stop()
            software.release()
        }
        log("released physicalMiB=${Pointer.physicalBytes() / 1048576}")
    }

    private fun hash(frame: Frame): Long {
        val hash = CRC32()
        val buffer = (frame.image[0] as ByteBuffer).duplicate()
        repeat(frame.imageHeight) { row ->
            buffer.limit(buffer.capacity())
            buffer.position(row * frame.imageStride)
            buffer.limit(row * frame.imageStride + frame.imageWidth * 4)
            hash.update(buffer)
        }
        return hash.value
    }
}
