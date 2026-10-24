package com.woka.feature.viewer.image

import androidx.compose.ui.unit.IntSize
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.global.avformat.AV_DISPOSITION_DEPENDENT
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

/** What one stream says about itself, read straight off the container. */
internal fun streamSize(format: AVFormatContext, stream: Int): IntSize {
    val codecpar = format.streams(stream).codecpar()
    return IntSize(codecpar.width(), codecpar.height())
}

/**
 * Video streams that are pictures in their own right.
 *
 * A HEIF grid item is stored as tiles, and FFmpeg exposes every tile as its own
 * stream flagged `AV_DISPOSITION_DEPENDENT`. Listing those as frames would show
 * a 300-frame strip of 512×512 fragments for what is one photograph.
 */
internal fun independentVideoStreams(format: AVFormatContext): List<Int> =
    (0 until format.nb_streams()).filter { index ->
        val stream = format.streams(index)
        stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO &&
            stream.disposition() and AV_DISPOSITION_DEPENDENT == 0
    }

internal fun frameCount(format: AVFormatContext, stream: Int): Int =
    format.streams(stream).nb_frames().toInt().coerceAtLeast(1)

/** From the stream's average frame rate; null when it is not a sane timing. */
internal fun frameDurationMs(format: AVFormatContext, stream: Int): Int? {
    val rate = format.streams(stream).avg_frame_rate()
    if (rate.num() <= 0 || rate.den() <= 0) return null
    return (1000L * rate.den() / rate.num()).toInt().takeIf { it > 0 }
}

/**
 * Opens [file] on one video stream and always closes it.
 *
 * [videoStreamIndex] is an **absolute container stream index**, the same thing
 * `openVideoGrabber` and `MediaTrack.index` mean by it, and it is set for every
 * frame including zero. Leaving zero on the container's own default looks
 * harmless and is not: on `burst.heic` JavaCV's default lands on **stream 1**,
 * so frame 0 and frame 1 decoded the same picture and the primary item was
 * never reached at all.
 *
 * `stop()`/`release()` are guarded separately, or a `start()` that threw is
 * masked by whatever `stop()` throws on top of it (F5).
 */
internal fun <T> onGrabber(
    file: File,
    videoStreamIndex: Int,
    block: (FFmpegFrameGrabber) -> T
): T {
    val grabber = FFmpegFrameGrabber(file)
    grabber.pixelFormat = AV_PIX_FMT_BGRA
    grabber.setOption("max_streams", MAX_STREAMS_OPTION)
    grabber.videoStream = videoStreamIndex
    return try {
        grabber.start()
        block(grabber)
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}
