package com.woka.probe

import com.woka.feature.viewer.video.engine.probe.HdrInfo
import com.woka.feature.viewer.video.engine.probe.readHdrInfo
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder_by_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_get_name
import org.bytedeco.ffmpeg.global.avformat.av_find_input_format
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.av_get_pix_fmt_name
import org.bytedeco.ffmpeg.global.avutil.av_log_get_level
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

/** A still that decodes to hundreds of frames is a container mistake, not a burst. */
private const val MAX_GRABS = 64

/** What the **bundled** FFmpeg — `org.bytedeco:ffmpeg 6.1.1-1.5.10` — makes of one file. */
data class FfmpegReport(
    val codec: String = "—",
    val size: String = "—",
    val grabbedFrames: Int = 0,
    val videoStreams: Int = 0,
    val pixelFormat: String = "—",
    val hdr: HdrInfo? = null,
    val failure: String? = null
) {
    val opened: Boolean get() = failure == null
}

/**
 * Opens [file], counts the frames that actually decode, and reads the colour
 * description off the video stream's `codecpar` — the same two fields
 * `readStreamHdrInfo` uses in the player, so a "yes" here means the existing
 * mapping would work unchanged on a still.
 */
fun probeFfmpeg(file: File): FfmpegReport = quietly {
    val grabber = FFmpegFrameGrabber(file)
    try {
        grabber.start()
        val codecpar = grabber.formatContext.streams(grabber.videoStream).codecpar()
        FfmpegReport(
            codec = avcodec_get_name(codecpar.codec_id()).string,
            size = "${grabber.imageWidth}×${grabber.imageHeight}",
            grabbedFrames = countFrames(grabber),
            videoStreams = countVideoStreams(grabber),
            // `codecpar.format()`, not `grabber.pixelFormat` — the latter is the
            // grabber's **output** format and reads `bgr24` for everything,
            // which hides exactly the 8-vs-10-bit answer Phase 5 needs.
            pixelFormat = pixelFormatName(codecpar.format()),
            hdr = readHdrInfo(codecpar.color_trc(), codecpar.color_primaries())
        )
    } catch (error: Throwable) {
        FfmpegReport(failure = error.brief())
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}

private fun countFrames(grabber: FFmpegFrameGrabber): Int {
    var count = 0
    while (count < MAX_GRABS && grabber.grabImage() != null) count++
    return count
}

/**
 * The number that gives a HEIC burst away.
 *
 * A HEIF container holds each of its images as **its own video stream**, so a
 * five-shot burst is five streams of one frame each — not one stream of five
 * frames. `FFmpegFrameGrabber` decodes exactly one of them (`videoStream`), so
 * counting `grabImage()` calls reports `1` for a burst and hides the whole
 * frame set. Whatever Phase 2b builds has to walk the streams.
 */
private fun countVideoStreams(grabber: FFmpegFrameGrabber): Int {
    val format = grabber.formatContext
    return (0 until format.nb_streams()).count { index ->
        format.streams(index).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO
    }
}

private fun pixelFormatName(format: Int): String =
    av_get_pix_fmt_name(format)?.string ?: "unknown($format)"

/**
 * Is a decoder or demuxer compiled into the bundled build at all.
 *
 * The distinction matters and is why both are asked: an `hevc` decoder without a
 * demuxer that can walk a `meta` box still cannot open a HEIC, and neither can a
 * demuxer without the decoder behind it.
 */
fun decoderPresent(name: String): Boolean = avcodec_find_decoder_by_name(name) != null

fun demuxerPresent(name: String): Boolean = av_find_input_format(name) != null

/**
 * FFmpeg writes to stderr from native code, and a probe that deliberately opens
 * files it cannot read would otherwise bury its own table in decoder errors.
 */
private fun <T> quietly(block: () -> T): T {
    val level = av_log_get_level()
    av_log_set_level(AV_LOG_QUIET)
    return try {
        block()
    } finally {
        av_log_set_level(level)
    }
}
