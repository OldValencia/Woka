package com.woka.probe

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.probe.readHdrInfo
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_PKT_DATA_DOVI_CONF
import org.bytedeco.ffmpeg.global.avcodec.av_packet_side_data_get
import org.bytedeco.ffmpeg.global.avfilter.avfilter_get_by_name
import org.bytedeco.ffmpeg.global.avutil.AV_FRAME_DATA_DOVI_RPU_BUFFER
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_side_data
import org.bytedeco.ffmpeg.global.avutil.av_frame_side_data_name
import java.io.File
import java.util.zip.CRC32
import kotlin.test.Test

/**
 * TEMPORARY (1.0.8 Dolby Vision investigation): native metadata on the author's real samples.
 * DOVI configuration's byte fields follow FFmpeg 8.1 `dovi_meta.h`, not guessed RPU offsets.
 * The configuration struct's total size is not ABI-stable; only this pinned-build probe reads it.
 */
class DolbySampleProbe {
    @Test
    fun `inspect Dolby profiles and accessible metadata`() {
        val root = File("build/dv-investigation").absoluteFile.also { it.mkdirs() }
        val rows = mutableListOf<String>()
        val files = listOf("Mystery Box Dolby Vision Profile 5.mp4", "Test Jellyfin 1080p DV P5.mp4",
            "Test Jellyfin 4K DV P8.1.mp4", "Test Jellyfin 4K DV P8.4.mp4",
            "Test Jellyfin 8K HEVC 10bit 150M.mp4", "Test Jellyfin 8K HEVC HDR10 150M.mp4")
        for (name in files) {
            val grabber = FreshFrameGrabber(File("D:/Movies/DV samples", name))
            try {
                grabber.setVideoOption("threads", "4")
                grabber.audioStream = Int.MAX_VALUE
                grabber.imageWidth = 400
                grabber.imageHeight = 225
                grabber.pixelFormat = AV_PIX_FMT_RGBA
                grabber.start()
                val params = grabber.formatContext.streams(grabber.videoStream).codecpar()
                val config = av_packet_side_data_get(params.coded_side_data(), params.nb_coded_side_data(), AV_PKT_DATA_DOVI_CONF)
                val bytes = config?.takeIf { !it.isNull && it.size() >= 9 }?.let { side ->
                    (0L..8L).map { side.data().get(it).toInt() and 255 }
                }
                rows += "$name size=${params.width()}x${params.height()} fps=${grabber.frameRate} " +
                    "trc=${params.color_trc()} primaries=${params.color_primaries()} matrix=${params.color_space()} range=${params.color_range()} " +
                    "classification=${readHdrInfo(params.color_trc(), params.color_primaries())} DOVIbytes=$bytes"
                for (second in listOf(1, 5, 10)) {
                    grabber.setTimestamp(second * 1_000_000L)
                    val picture = grabber.grabImage()?.opaque as? AVFrame ?: error("No AVFrame")
                    val side = (0 until picture.nb_side_data()).map { index ->
                        val data = picture.side_data(index)
                        "${av_frame_side_data_name(data.type()).string}:${data.size()}"
                    }
                    val rpu = av_frame_get_side_data(picture, AV_FRAME_DATA_DOVI_RPU_BUFFER)
                    val raw = rpu?.let { ByteArray(it.size().toInt()).also { bytes -> it.data().get(bytes) } }
                    val crc = raw?.let { bytes -> CRC32().apply { update(bytes) }.value }
                    if (raw != null) File(root, "$name-$second.rpu.bin").writeBytes(raw)
                    rows += "  t=$second ptsUs=${grabber.timestamp} side=$side rpuCRC=$crc"
                }
            } catch (failure: Throwable) {
                rows += "$name FAILED ${failure.message}"
            } finally {
                runCatching { grabber.stop() }
                runCatching { grabber.release() }
            }
        }
        val frameType = AVFrame::class.java
        for (type in listOf("AVDOVIDecoderConfigurationRecord", "AVDOVIMetadata", "AVDOVIColorMetadata")) {
            val packageName = frameType.packageName
            val present = runCatching { Class.forName("$packageName.$type") }.isSuccess
            rows += "binding $type=$present"
        }
        for (filter in listOf("libplacebo", "zscale", "tonemap")) {
            rows += "filter $filter=${avfilter_get_by_name(filter)?.isNull == false}"
        }
        File(root, "metadata.txt").writeText(rows.joinToString("\n"))
        rows.forEach { println("DVSAMPLE $it") }
    }
}
