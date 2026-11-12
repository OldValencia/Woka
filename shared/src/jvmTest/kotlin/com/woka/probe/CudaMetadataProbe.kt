package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.CudaVideoDecoder
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.ThreadedVideoConverter
import kotlinx.coroutines.CancellationException
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FrameGrabber
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.zip.CRC32
import kotlin.test.Test

/** TEMPORARY (EK-002): non-empty side data, cancellation and device release on the owned decoder. */
class CudaMetadataProbe {
    @Test
    fun `measure native metadata and teardown without JavaCV private access`() {
        assumeTrue("Opt in with woka.probe.8k", System.getProperty("woka.probe.8k") == "true")
        val report = File("build/dv-investigation/accelerated/native-metadata.txt")
        report.parentFile.mkdirs()
        report.writeText("deviceBaselineMiB=${gpuMb()}\n")
        for (name in listOf("Test Jellyfin 4K DV P8.1.mp4", "Hybrid HDR10Plus DV Sample.mkv")) {
            val file = File("D:/Movies/DV samples", name)
            val header = FreshFrameGrabber(file)
            val software = FreshFrameGrabber(file)
            header.imageMode = FrameGrabber.ImageMode.RAW
            software.pixelFormat = AV_PIX_FMT_RGBA
            software.imageWidth = 1440
            software.imageHeight = 810
            for (grabber in listOf(header, software)) {
                grabber.audioStream = Int.MAX_VALUE
                grabber.setVideoOption("threads", "4")
            }
            try {
                header.start()
                software.start()
                var alive = true
                CudaVideoDecoder(header.formatContext, header.videoStream) { alive }.use { decoder ->
                    ThreadedVideoConverter(1440, 810).use { converter ->
                        for (at in listOf(1_000_000L, 5_000_000L, 20_000_000L, 2_000_000L)) {
                            decoder.seek(at)
                            software.setTimestamp(at)
                            val expected = checkNotNull(software.grabImage())
                            val raw = checkNotNull(decoder.next())
                            val frame = converter.convert(raw, decoder.timestampUs)
                            val metadata = fingerprint(frame.opaque as AVFrame)
                            report.appendText("$name at=$at sameTimestamp=${expected.timestamp == frame.timestamp} " +
                                "sameMetadata=${metadata == fingerprint(checkNotNull(software.lastPicture))} " +
                                "metadata=$metadata\n")
                        }
                        report.appendText("$name deviceOpenMiB=${gpuMb()}\n")
                        alive = false
                        val cancelled = try {
                            decoder.next()
                            false
                        } catch (_: CancellationException) { true }
                        report.appendText("$name cancelled=$cancelled\n")
                    }
                }
            } finally {
                header.release()
                software.release()
            }
            report.appendText("$name deviceReleasedMiB=${gpuMb()}\n")
        }
        println(report.readText())
    }

    private fun fingerprint(frame: AVFrame): String {
        val side = (0 until frame.nb_side_data()).map { index ->
            val data = frame.side_data(index)
            val hash = CRC32()
            hash.update(data.data().capacity(data.size().toLong()).asByteBuffer())
            "${data.type()}:${data.size()}:${hash.value}"
        }.sorted()
        return "${frame.color_trc()}/${frame.color_primaries()}/${frame.colorspace()}/${frame.color_range()}:$side"
    }
}
