package com.woka.ui.viewer.player

import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.Frame
import java.nio.ShortBuffer

/**
 * `atempo` filter over the decoded PCM (§6). Built only when the speed is not 1x,
 * so normal playback keeps the original untouched path. Reading samples faster
 * would raise the pitch; this resamples instead, and its 0.5–2.0 range is exactly
 * the range the UI offers.
 *
 * Instances are single-use per continuous stretch of playback — see [audioReseek]
 * for why a seek throws the graph away rather than reusing it.
 */
internal class AudioTempoFilter(
    private val sampleRate: Int,
    private val channels: Int,
    speed: Float
) {
    private val filter: FFmpegFrameFilter? = try {
        FFmpegFrameFilter("atempo=$speed", channels).apply {
            this.sampleFormat = AV_SAMPLE_FMT_S16
            this.sampleRate = this@AudioTempoFilter.sampleRate
            start()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /**
     * Pushes one decoded frame and drains every resampled buffer it produces.
     * Returns null when the filter is unusable, so the caller falls back to the
     * unfiltered path instead of dropping the sound entirely. One input frame can
     * yield zero or several output buffers — atempo does not preserve framing.
     */
    fun process(frame: Frame): List<ByteArray>? {
        val f = filter ?: return null
        return try {
            f.push(frame)
            val chunks = mutableListOf<ByteArray>()
            while (true) {
                val out = f.pull() ?: break
                val samples = out.samples ?: continue
                // atempo can emit a zero-sample frame; an empty buffer is worse
                // than useless downstream, so it never leaves the filter.
                val pcm = (samples[0] as ShortBuffer).toPcmBytes()
                if (pcm.isNotEmpty()) chunks += pcm
            }
            chunks
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun close() {
        try {
            filter?.stop()
            filter?.release()
        } catch (_: Exception) {
        }
    }
}
