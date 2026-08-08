package com.woka.feature.viewer.image

import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT2020
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_ARIB_STD_B67
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_SMPTE2084
import java.io.File

/**
 * A still's colour description, read from FFmpeg's demuxer — **whatever decoder
 * draws its pixels**.
 *
 * Deliberately not routed through [stillDecoderFor]. Skia cannot answer this
 * question at all: measured 2026-08-07, it describes a PNG carrying a valid PQ
 * `cICP` chunk as "gamma close to sRGB", because `ColorSpace` exposes three
 * booleans and no transfer function. FFmpeg reads the same file as `PQ + wide`.
 * So a PQ PNG — which skia decodes and FFmpeg only inspects — would report SDR
 * if this followed the pixel path, and would be the one HDR still the viewer
 * still got wrong.
 *
 * Cost is paid where it is owed: a HEIF or AVIF carries its colour in a
 * container box that opening already read, and only the formats FFmpeg does
 * *not* decode here — PNG, JPEG, TIFF — need the deeper probe, those being the
 * small files anyway.
 */
internal fun readStillColor(file: File): StillColorInfo = runCatching {
    // A container that FFmpeg also decodes carries its colour in a box that
    // `avformat_open_input` has already read. Everything else keeps it in the
    // codec header, where only `find_stream_info` finds it — measured: without
    // this, `hdr_pq.png` reads SDR while `hdr_pq.avif` reads PQ. The deep probe
    // is affordable precisely where it is needed, those being the small files.
    val deep = stillDecoderFor(file) !== FfmpegStillDecoder
    onFormatContext(file, findStreamInfo = deep) { format ->
        val stream = firstVideoStreamOf(format) ?: return@onFormatContext StillColorInfo.Sdr
        val codecpar = format.streams(stream).codecpar()
        StillColorInfo(
            transfer = transferOf(codecpar.color_trc()),
            isWideGamut = codecpar.color_primaries() == AVCOL_PRI_BT2020
        )
    }
}.getOrDefault(StillColorInfo.Sdr)

/**
 * The first video stream of any kind, tile or not: a tiled HEIF's colour tags
 * sit on its tiles, and every tile of one picture shares them.
 */
private fun firstVideoStreamOf(format: AVFormatContext): Int? =
    (0 until format.nb_streams()).firstOrNull {
        format.streams(it).codecpar().width() > 0
    }

private fun transferOf(colorTrc: Int): TransferKind = when (colorTrc) {
    AVCOL_TRC_SMPTE2084 -> TransferKind.PQ
    AVCOL_TRC_ARIB_STD_B67 -> TransferKind.HLG
    else -> TransferKind.SDR
}
