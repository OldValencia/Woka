package com.woka.feature.viewer.image

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_JPEG
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV422P
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV440P
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV444P
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUVJ420P
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUVJ422P
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUVJ440P
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUVJ444P
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.swscale.sws_alloc_context
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale_frame

private const val BYTES_PER_PIXEL = 4

/**
 * Converts a decoded tile to BGRA.
 *
 * **`sws_scale` is not usable here and `sws_scale_frame` is.** The old
 * pointer-array call returns `-22` (`EINVAL`) for every tile these files
 * contain, because a HEIF tile decodes to `yuvj420p` and FFmpeg 8's swscale no
 * longer accepts the deprecated YUVJ formats. The frame API takes the format
 * and the colour range off the frames themselves, which is also what makes the
 * range handling below correct rather than merely quiet.
 *
 * [shrinkBy] divides the tile on the way out, which is what makes a preview of
 * a 36-megapixel picture cost almost nothing: swscale is already converting
 * every pixel, so scaling in the same pass is close to free, and the 138 MB
 * canvas is never allocated at all. It must be a power of two — see
 * [shrunkBy] for why the tiles stop tessellating otherwise.
 */
internal fun AVFrame.toBgraTile(name: String, shrinkBy: Int = 1): DecodedTile {
    normalizeDeprecatedFormat()
    val destination = av_frame_alloc()
    val scaler = sws_alloc_context() ?: error("no swscale context for $name")
    try {
        destination.format(AV_PIX_FMT_BGRA)
        destination.width(width() / shrinkBy)
        destination.height(height() / shrinkBy)
        check(av_frame_get_buffer(destination, 0) >= 0) { "no BGRA buffer for $name" }
        val scaled = sws_scale_frame(scaler, destination, this)
        check(scaled >= 0) { "sws_scale_frame failed with $scaled on $name" }
        return destination.copyOut()
    } finally {
        av_frame_free(destination)
        sws_freeContext(scaler)
    }
}

/**
 * Relabels the deprecated YUVJ formats, which swscale dropped in FFmpeg 8.
 *
 * `yuvj420p` and `yuv420p` are byte-identical — the J means nothing but "full
 * range" — so this is a relabel, not a conversion. Setting the range alongside
 * it is what keeps the picture from coming out washed out: dropped on the
 * floor, full-range luma would be read as if it were limited-range.
 */
private fun AVFrame.normalizeDeprecatedFormat() {
    val plain = when (format()) {
        AV_PIX_FMT_YUVJ420P -> AV_PIX_FMT_YUV420P
        AV_PIX_FMT_YUVJ422P -> AV_PIX_FMT_YUV422P
        AV_PIX_FMT_YUVJ440P -> AV_PIX_FMT_YUV440P
        AV_PIX_FMT_YUVJ444P -> AV_PIX_FMT_YUV444P
        else -> return
    }
    format(plain)
    color_range(AVCOL_RANGE_JPEG)
}

/**
 * Copies the BGRA plane out row by row.
 *
 * Row by row and not in one block: `linesize(0)` is padded up for alignment and
 * is wider than the picture, so a single copy would shear the image by a few
 * pixels more on every row. The capacity is set explicitly because the pointer
 * a frame hands back does not carry one, and a copy from a zero-capacity
 * pointer silently reads nothing.
 */
private fun AVFrame.copyOut(): DecodedTile {
    val stride = linesize(0)
    val rowBytes = width() * BYTES_PER_PIXEL
    val bytes = ByteArray(rowBytes * height())
    val plane = data(0).capacity(stride.toLong() * height())
    (0 until height()).forEach { row ->
        plane.position(row.toLong() * stride).get(bytes, row * rowBytes, rowBytes)
    }
    return DecodedTile(width(), height(), bytes)
}
