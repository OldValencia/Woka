package com.woka.ui.viewer.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avcodec.AVSubtitle
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avformat.*
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_SUBTITLE
import org.bytedeco.javacpp.IntPointer
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pre-parses text subtitle tracks (§2.2) on open into [Cue] lists and updates
 * [VideoPlayerTracks.activeCue] synced to [VideoPlayerState.currentPositionMs].
 */
internal fun VideoPlayerState.startSubtitleDecoderLoop() {
    scope.launch(Dispatchers.IO) {
        val subtitleTracks = tracks.mediaTracks.subtitle
        if (subtitleTracks.isEmpty()) return@launch

        val textTrackIndices = subtitleTracks
            .filter { it.isTextBased != false }
            .map { it.index }
            .toSet()

        val textCues = if (textTrackIndices.isNotEmpty()) {
            decodeTextSubtitles(file.absolutePath, textTrackIndices)
        } else {
            emptyMap()
        }

        while (isActive) {
            val selectedIndex = tracks.selectedSubtitleIndex
            if (selectedIndex == null) {
                tracks.activeCue = null
            } else {
                val cues = textCues[selectedIndex]
                tracks.activeCue = findActiveCue(cues, currentPositionMs)
            }
            delay(50.milliseconds)
        }
    }
}

private fun decodeTextSubtitles(
    filepath: String,
    textIndices: Set<Int>
): Map<Int, List<Cue>> {
    val result = mutableMapOf<Int, MutableList<Cue>>()
    val fc = AVFormatContext(null)
    if (avformat_open_input(fc, filepath, null, null) != 0) return emptyMap()
    if (avformat_find_stream_info(fc, null as AVDictionary?) < 0) {
        avformat_close_input(fc)
        return emptyMap()
    }

    val codecContexts = openCodecContexts(fc, textIndices)
    val pkt = AVPacket()
    val sub = AVSubtitle()
    val gotSub = IntPointer(1)

    try {
        while (av_read_frame(fc, pkt) >= 0) {
            val idx = pkt.stream_index()
            val ctx = codecContexts[idx]
            if (ctx != null) {
                val list = result.getOrPut(idx) { mutableListOf() }
                SubtitleParser.decodePacket(ctx, fc.streams(idx), pkt, sub, gotSub, list)
            }
            av_packet_unref(pkt)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        codecContexts.values.forEach { avcodec_free_context(it) }
        avformat_close_input(fc)
    }
    return result
}

private fun openCodecContexts(
    fc: AVFormatContext,
    indices: Set<Int>
): Map<Int, AVCodecContext> {
    val map = mutableMapOf<Int, AVCodecContext>()
    for (idx in indices) {
        val stream = fc.streams(idx) ?: continue
        if (stream.codecpar()?.codec_type() != AVMEDIA_TYPE_SUBTITLE) continue
        val codec = avcodec_find_decoder(stream.codecpar().codec_id()) ?: continue
        val ctx = avcodec_alloc_context3(codec) ?: continue
        if (avcodec_parameters_to_context(ctx, stream.codecpar()) >= 0 &&
            avcodec_open2(ctx, codec, null as AVDictionary?) >= 0
        ) {
            map[idx] = ctx
        } else {
            avcodec_free_context(ctx)
        }
    }
    return map
}

private fun findActiveCue(cues: List<Cue>?, positionMs: Long): Cue? {
    if (cues.isNullOrEmpty()) return null
    for (cue in cues) {
        if (positionMs in cue.startMs..cue.endMs) {
            return cue
        }
    }
    return null
}
