package com.woka.probe

import com.woka.feature.viewer.video.engine.subtitle.SubtitleWindow
import com.woka.feature.viewer.video.engine.subtitle.openSubtitleWindow
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_ALL
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.junit.Assume.assumeTrue
import kotlin.test.Test

/**
 * Where the time to a first subtitle goes on a real film (2026-11-20: 1–3 s on a 63.6 GB
 * `mkv`). Opt in with `woka.probe.subs=<film>` and `woka.probe.subs.index=<stream>`.
 * Reports, does not assert: the open alone, the stream analysis alone, then a window's
 * first answer at several positions, each from a fresh open — the case of a track chosen.
 */
class SubtitleWindowProbe {

    @Test
    fun `time to the first subtitle`() {
        val film = System.getProperty("woka.probe.subs")
        assumeTrue("Opt in with woka.probe.subs", film != null)
        val index = System.getProperty("woka.probe.subs.index", "7").toInt()

        repeat(2) { round ->
            val fc = AVFormatContext(null)
            val opened = timed { avformat_open_input(fc, film, null, null) }
            val analysed = timed { avformat_find_stream_info(fc, null as AVDictionary?) }
            avformat_close_input(fc)
            println("SUBS round $round open ${opened} ms, find_stream_info ${analysed} ms")
        }
        seekReport(film, index)
        listOf(0L, 600_000L, 3_600_000L).forEach { atMs ->
            var window: SubtitleWindow? = null
            val open = timed { window = openSubtitleWindow(film, index, bitmap = false) }
            val first = timed { window?.textAt(atMs) }
            val next = timed { window?.textAt(atMs + 1_000L) }
            println("SUBS at ${atMs / 1000} s: window open ${open} ms, first answer ${first} ms, next ${next} ms")
            window?.close()
        }
    }

    /** What a seek to one hour returns, and where the first packet after it lands, per discard set. */
    private fun seekReport(film: String, index: Int) {
        listOf("none discarded", "all but subtitle+audio", "all but subtitle+audio, seek on audio").forEach { mode ->
            val fc = AVFormatContext(null)
            avformat_open_input(fc, film, null, null)
            avformat_find_stream_info(fc, null as AVDictionary?)
            val audio = (0 until fc.nb_streams()).first { fc.streams(it).codecpar().codec_type() == AVMEDIA_TYPE_AUDIO }
            if (mode != "none discarded") {
                for (i in 0 until fc.nb_streams()) if (i != index && i != audio) fc.streams(i).discard(AVDISCARD_ALL)
            }
            val onAudio = mode.endsWith("seek on audio")
            val stream = if (onAudio) audio else -1
            val ts = if (onAudio) 3_600L * fc.streams(audio).time_base().den() / fc.streams(audio).time_base().num() else 3_600_000_000L
            var code = 0
            val seek = timed { code = av_seek_frame(fc, stream, ts, AVSEEK_FLAG_BACKWARD) }
            val pkt = AVPacket()
            var firstMs = -1L
            val read = timed {
                if (av_read_frame(fc, pkt) >= 0) {
                    val tb = fc.streams(pkt.stream_index()).time_base()
                    firstMs = pkt.pts() * tb.num() * 1000L / tb.den()
                }
            }
            println("SUBS seek [$mode]: code $code in $seek ms, first packet at $firstMs ms after $read ms")
            avformat_close_input(fc)
        }
    }

    private fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
