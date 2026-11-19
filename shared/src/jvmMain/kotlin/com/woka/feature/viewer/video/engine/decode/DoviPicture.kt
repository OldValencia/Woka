package com.woka.feature.viewer.video.engine.decode

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT2020
import org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_JPEG
import org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_RGB
import org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_SMPTE2084
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_copy_props
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable
import org.bytedeco.ffmpeg.global.avutil.av_frame_remove_side_data
import org.bytedeco.javacv.Frame

/** One reusable full-resolution PQ picture; filter graphs may retain a ref, so writes use COW. */
internal class DoviPicture(width: Int, height: Int) : AutoCloseable {
    val picture: AVFrame = checkNotNull(av_frame_alloc()).also { check(!it.isNull) }
    private val frame = Frame()

    init {
        try {
            picture.width(width).height(height).format(AV_PIX_FMT_RGBA)
            check(av_frame_get_buffer(picture, 32) >= 0)
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun writable() { check(av_frame_make_writable(picture) >= 0) }

    fun copyProperties(source: AVFrame) {
        while (picture.nb_side_data() > 0) av_frame_remove_side_data(picture, picture.side_data(0).type())
        check(av_frame_copy_props(picture, source) >= 0)
        picture.color_primaries(AVCOL_PRI_BT2020).color_trc(AVCOL_TRC_SMPTE2084)
        picture.colorspace(AVCOL_SPC_RGB).color_range(AVCOL_RANGE_JPEG)
    }

    fun frame(source: Frame): Frame {
        frame.imageWidth = picture.width()
        frame.imageHeight = picture.height()
        frame.imageDepth = Frame.DEPTH_UBYTE
        frame.imageChannels = 4
        frame.imageStride = picture.linesize(0)
        frame.image = arrayOf(picture.data(0).capacity(frame.imageStride.toLong() * frame.imageHeight).asByteBuffer())
        frame.opaque = picture
        frame.timestamp = source.timestamp
        frame.streamIndex = source.streamIndex
        frame.keyFrame = source.keyFrame
        frame.pictType = source.pictType
        frame.type = Frame.Type.VIDEO
        return frame
    }

    override fun close() { av_frame_free(picture) }
}
