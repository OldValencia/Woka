package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_DOVI_RPU_BUFFER
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_side_data
import org.bytedeco.ffmpeg.global.avutil.av_get_pix_fmt_name
import org.bytedeco.ffmpeg.global.avutil.avutil_version
import org.bytedeco.javacv.FrameGrabber
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.zip.CRC32
import kotlin.test.Test

/** DV5-001: opt-in table, no production guard bypass outside this probe. */
class DoviP5ReferenceProbe {
    @Test
    fun `record raw P5 frame metadata before colour conversion`() {
        assumeTrue(System.getProperty("woka.probe.dv5") == "true")
        val output = File("build/dv-p5").absoluteFile.also { it.mkdirs() }
        val library = File(System.getProperty("woka.probe.dv5.library"))
        val report = mutableListOf("libavutil=${avutil_version()} library=${library.absolutePath}")
        val samples = listOf("Mystery Box Dolby Vision Profile 5.mp4", "Test Jellyfin 1080p DV P5.mp4")
        DoviMetadataAccess(library.toPath()).use { access ->
            for ((index, name) in samples.withIndex()) {
                for (mode in listOf("sequential", "seek")) {
                    record(File("D:/Movies/DV samples", name), index, mode, access, output, report)
                }
            }
        }
        File(output, "metadata.txt").writeText(report.joinToString("\n"))
        report.forEach { println("DV5 $it") }
    }

    private fun record(
        file: File,
        index: Int,
        mode: String,
        access: DoviMetadataAccess,
        output: File,
        report: MutableList<String>
    ) {
        val grabber = FreshFrameGrabber(file, allowUnsupportedColour = true)
        try {
            grabber.imageMode = FrameGrabber.ImageMode.RAW
            grabber.audioStream = Int.MAX_VALUE
            grabber.setVideoOption("threads", "4")
            grabber.start()
            for (second in listOf(1, 5, 10)) {
                if (mode == "seek") grabber.setTimestamp(second * 1_000_000L)
                do {
                    checkNotNull(grabber.grabImage()) { "EOF before target $second" }
                } while (grabber.timestamp < second * 1_000_000L)
                val picture = checkNotNull(grabber.lastPicture)
                val values = access.read(picture)
                val rpu = av_frame_get_side_data(picture, AV_FRAME_DATA_DOVI_RPU_BUFFER)
                val raw = ByteArray(rpu.size().toInt()).also { rpu.data().get(it) }
                val crc = CRC32().apply { update(raw) }.value
                val stem = "p5-$index-$mode-$second"
                if (mode == "sequential") {
                    File(output, "$stem.yuv420p10le").outputStream().use { stream ->
                        for (plane in 0..2) {
                            val width = if (plane == 0) picture.width() else picture.width() / 2
                            val height = if (plane == 0) picture.height() else picture.height() / 2
                            val row = ByteArray(width * 2)
                            val pointer = picture.data(plane)
                            for (y in 0 until height) {
                                pointer.position(y.toLong() * picture.linesize(plane)).get(row)
                                stream.write(row)
                            }
                            pointer.position(0)
                        }
                    }
                }
                File(output, "$stem.metadata.csv").writeText(values.mapIndexed { i, v -> "$i,$v" }.joinToString("\n"))
                File(output, "$stem.rpu.bin").writeBytes(raw)
                val curves = (0..2).joinToString { c ->
                    val start = 64 + 210*c
                    val count = values[start].toInt()
                    val methods = (0 until count-1).map { values[start+10+25*it].toInt() }
                    "c$c:pivots=$count methods=$methods"
                }
                report += "$stem file=${file.name} ptsUs=${grabber.timestamp} " +
                    "size=${picture.width()}x${picture.height()} format=${av_get_pix_fmt_name(picture.format()).string} " +
                    "stride=${picture.linesize(0)},${picture.linesize(1)},${picture.linesize(2)} " +
                    "rpuCRC=$crc bits=${values[1]} full=${values[2]} denom=${values[3]} " +
                    "sourcePQ=${values[4]},${values[5]} L1=${values.slice(6..9)} " +
                    "flags=${values.slice(10..15)} signal=${values.slice(37..43)} $curves"
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
