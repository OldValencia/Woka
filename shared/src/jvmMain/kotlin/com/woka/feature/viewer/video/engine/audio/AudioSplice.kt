package com.woka.feature.viewer.video.engine.audio

import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.Buffer
import java.nio.ShortBuffer

/*
 * The sample and result-time arithmetic behind `AudioRanges` (ME-233), split out of it for
 * the size mandate.
 */

/** Source [videoMs] as the audio stream's µs, under the user's audio offset. */
internal fun audioUs(player: VideoPlayerState, videoMs: Long): Long = (videoMs - player.audioDelayMs) * 1000L

internal fun framesAt(us: Long, rate: Int): Int = (us * rate / 1_000_000L).toInt()

internal fun silenceBytes(grabber: FFmpegFrameGrabber, holdMs: Long): Int =
    (grabber.sampleRate.toLong() * holdMs / 1000L).toInt() * grabber.audioChannels * 2

/** A linear ramp over the first (rising) or last [length] frames of interleaved [samples]. */
internal fun ramp(samples: ShortArray, channels: Int, length: Int, rising: Boolean) {
    val frames = samples.size / channels
    val span = minOf(length, frames)
    for (i in 0 until span) {
        val frame = if (rising) i else frames - 1 - i
        val gain = i.toFloat() / span
        for (c in 0 until channels) {
            val at = frame * channels + c
            samples[at] = (samples[at] * gain).toInt().toShort()
        }
    }
}

internal fun SourceRange.holdsVideo(videoMs: Long): Boolean = if (isFreeze) videoMs == fromMs else videoMs in fromMs until toMs

internal fun startOf(ranges: List<SourceRange>, index: Int): Long = ranges.take(index).sumOf { it.lengthMs }

/** Source [videoMs] in range [index] as a position on the result. */
internal fun resultOf(ranges: List<SourceRange>, index: Int, videoMs: Long): Long {
    val range = ranges.getOrNull(index) ?: return videoMs
    if (range.isFreeze) return startOf(ranges, index)
    return startOf(ranges, index) + ((videoMs - range.fromMs) / range.speed.toDouble()).toLong()
}

/** [kept] samples of [frame] onto [channel], through [tempo] when the range plays at a speed. */
internal suspend fun sendKept(frame: Frame, kept: ShortArray, tempo: AudioTempoFilter?, channel: Channel<ByteArray>) {
    val wrapped = ShortBuffer.wrap(kept)
    val chunks = tempo?.let {
        frame.samples = arrayOf<Buffer>(wrapped)
        it.process(frame)
    }
    if (chunks != null) chunks.forEach { channel.send(it) } else channel.send(wrapped.toPcmBytes())
}
