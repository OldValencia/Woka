package com.woka.feature.viewer.video.engine.probe

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_PROP_TEXT_SUB
import org.bytedeco.ffmpeg.global.avcodec.avcodec_descriptor_get
import org.bytedeco.ffmpeg.global.avcodec.avcodec_get_name
import org.bytedeco.ffmpeg.global.avformat.AV_DISPOSITION_DEFAULT
import org.bytedeco.ffmpeg.global.avformat.AV_DISPOSITION_FORCED
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_SUBTITLE
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.av_channel_layout_describe
import org.bytedeco.ffmpeg.global.avutil.av_dict_get
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacv.FFmpegFrameGrabber
import com.woka.feature.viewer.video.model.MediaTrack
import com.woka.feature.viewer.video.model.MediaTracks
import com.woka.feature.viewer.video.model.TrackType

/**
 * Walks the container's streams (§2.0) and builds a [MediaTracks] model. Runs
 * on open, on the already-started grabber, so it reuses its format context.
 */
internal fun probeMediaTracks(grabber: FFmpegFrameGrabber): MediaTracks {
    val fc = grabber.formatContext ?: return MediaTracks.EMPTY
    val video = mutableListOf<MediaTrack>()
    val audio = mutableListOf<MediaTrack>()
    val subtitle = mutableListOf<MediaTrack>()
    for (i in 0 until fc.nb_streams()) {
        val stream = fc.streams(i) ?: continue
        when (stream.codecpar()?.codec_type()) {
            AVMEDIA_TYPE_VIDEO -> video += stream.toMediaTrack(i, video.size + 1, TrackType.Video)
            AVMEDIA_TYPE_AUDIO -> audio += stream.toMediaTrack(i, audio.size + 1, TrackType.Audio)
            AVMEDIA_TYPE_SUBTITLE ->
                subtitle += stream.toMediaTrack(i, subtitle.size + 1, TrackType.Subtitle)
        }
    }
    return MediaTracks(video, audio, subtitle)
}

private fun AVStream.toMediaTrack(index: Int, number: Int, type: TrackType): MediaTrack {
    val par = codecpar()
    val codecId = par.codec_id()
    val disposition = disposition()
    val isVideo = type == TrackType.Video
    val audioInfo = if (type == TrackType.Audio) par.channelInfo() else null
    return MediaTrack(
        index = index,
        number = number,
        type = type,
        codec = codecName(codecId),
        language = metaValue("language"),
        title = metaValue("title"),
        isDefault = disposition and AV_DISPOSITION_DEFAULT != 0,
        isForced = disposition and AV_DISPOSITION_FORCED != 0,
        width = if (isVideo) par.width().takeIf { it > 0 } else null,
        height = if (isVideo) par.height().takeIf { it > 0 } else null,
        frameRate = if (isVideo) avg_frame_rate().toFps() else null,
        bitRate = if (isVideo) par.bit_rate().takeIf { it > 0 } else null,
        channels = audioInfo?.first,
        channelLayout = audioInfo?.second,
        isTextBased = if (type == TrackType.Subtitle) subtitleIsTextBased(codecId) else null
    )
}

private fun AVRational.toFps(): Double? {
    val den = den()
    return if (den != 0) num().toDouble() / den else null
}

private fun AVStream.metaValue(key: String): String? {
    val entry: AVDictionaryEntry = av_dict_get(metadata(), key, null, 0) ?: return null
    return entry.value()?.string?.takeIf { it.isNotBlank() }
}

private fun AVCodecParameters.channelInfo(): Pair<Int, String?> {
    val layout = ch_layout()
    val buffer = BytePointer(256L)
    av_channel_layout_describe(layout, buffer, buffer.capacity())
    // The layout text is printable ASCII; getString() reads the whole buffer, so
    // stop at the first non-printable byte to drop the uninitialised tail (which
    // otherwise trails a stray glyph).
    val description = buffer.string
        .takeWhile { it.code in 32..126 }
        .trim()
        .takeIf { it.isNotBlank() }
    return layout.nb_channels() to description
}

private fun codecName(codecId: Int): String =
    avcodec_get_name(codecId)?.string?.takeIf { it.isNotBlank() } ?: "unknown"

private fun subtitleIsTextBased(codecId: Int): Boolean {
    val descriptor = avcodec_descriptor_get(codecId) ?: return true
    return descriptor.props() and AV_CODEC_PROP_TEXT_SUB != 0
}
