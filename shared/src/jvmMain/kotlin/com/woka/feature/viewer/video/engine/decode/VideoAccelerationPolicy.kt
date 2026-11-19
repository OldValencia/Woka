package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.probe.clockwiseTurns
import com.woka.feature.viewer.video.engine.probe.isDoviProfile5
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P10LE
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber

/**
 * EK-002 starts with the measured 8K MP4/Main10 path; it is not ME-236 lane acceleration.
 * Other platforms, sizes, codecs and picture graphs retain software playback. A matching
 * header only permits a device attempt; the CUDA codec still has to open and decode.
 */
internal fun FFmpegFrameGrabber.canAccelerateVideo(): Boolean {
    if (!System.getProperty("os.name").startsWith("Windows")) return false
    if (imageMode == FrameGrabber.ImageMode.RAW || pixelFormat != AV_PIX_FMT_RGBA) return false
    val format = formatContext ?: return false
    if (videoStream < 0 || videoStream >= format.nb_streams()) return false
    if (isDoviProfile5()) return false
    val parameters = format.streams(videoStream).codecpar()
    return format.iformat().name().string.split(',').contains("mp4") &&
        parameters.codec_id() == AV_CODEC_ID_HEVC && parameters.format() == AV_PIX_FMT_YUV420P10LE &&
        parameters.width() == 7680 && parameters.height() == 4320 && clockwiseTurns == 0 &&
        imageWidth in 2..3840 && imageHeight in 2..2160 && !hasAudio()
}
