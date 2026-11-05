package com.woka.feature.editor.engine.video.render

import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import java.nio.ShortBuffer

/**
 * An encode's sound, kept level with its picture clip by clip (1.0.6 review).
 *
 * The picture is placed by timestamp and the sound appended by sample count, so anything a
 * clip's sound came up short by — its tail lost when the video passed the cut first (a muxer
 * interleaves up to a second), the `atempo` buffer dropped when the filter was stopped, a
 * source whose sound starts after its picture — moved every later clip's sound earlier, and a
 * film with many cuts drifted out of sync. So the sound is **counted**: each clip's is cut to
 * the sample at its range, the tempo filter is drained at the end, and silence makes up any
 * shortfall before the clip's first sound and after its last.
 *
 * Interleaved 16-bit throughout — what the render's grabber is asked for.
 */
internal class RenderAudio(
    private val recorder: FFmpegFrameRecorder,
    private val sampleRate: Int,
    private val channels: Int
) {
    /** Sample frames written so far. */
    private var written = 0L

    val present: Boolean get() = sampleRate > 0 && channels > 0

    /** Silence up to [outUs] on the output, when the sound so far falls short of it. */
    fun padTo(outUs: Long) {
        if (!present) return
        var short = outUs * sampleRate / 1_000_000L - written
        while (short > 0) {
            val count = minOf(short, sampleRate.toLong()).toInt()
            write(ShortArray(count * channels))
            short -= count
        }
    }

    /**
     * [frame]'s samples between source [fromUs] and [untilUs], given the frame starts at
     * [atUs] — cut to the sample, as the preview's splice does. Null when none fall inside.
     */
    fun within(frame: Frame, atUs: Long, fromUs: Long, untilUs: Long): Frame? {
        val pcm = frame.samples?.firstOrNull() as? ShortBuffer ?: return null
        val frames = pcm.remaining() / channels
        val keepFrom = frameAt(fromUs - atUs).coerceIn(0, frames)
        val keepTo = frameAt(untilUs - atUs).coerceIn(keepFrom, frames)
        if (keepTo == keepFrom) return null
        if (keepFrom == 0 && keepTo == frames) return frame
        val kept = ShortArray((keepTo - keepFrom) * channels)
        pcm.position(pcm.position() + keepFrom * channels)
        pcm.get(kept)
        frame.samples = arrayOf(ShortBuffer.wrap(kept))
        return frame
    }

    /** [samples] through [tempo] into the output, counted. */
    fun record(tempo: FFmpegFrameFilter?, samples: Frame) {
        if (tempo == null) return count(samples)
        tempo.push(samples)
        while (true) count(tempo.pullSamples() ?: break)
    }

    /** What [tempo] still holds at the clip's end, then the filter closed. */
    fun drain(tempo: FFmpegFrameFilter?) {
        tempo ?: return
        runCatching {
            tempo.push(null)
            while (true) count(tempo.pullSamples() ?: break)
        }
        runCatching {
            tempo.stop()
            tempo.release()
        }
    }

    private fun count(samples: Frame) {
        val pcm = samples.samples?.firstOrNull() as? ShortBuffer
        written += (pcm?.remaining() ?: 0) / channels.coerceAtLeast(1)
        recorder.record(samples)
    }

    private fun write(silence: ShortArray) {
        written += silence.size / channels
        recorder.recordSamples(sampleRate, channels, ShortBuffer.wrap(silence))
    }

    private fun frameAt(us: Long): Int = (us * sampleRate / 1_000_000L).toInt()
}
