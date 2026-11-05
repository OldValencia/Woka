package com.woka.feature.viewer.video.engine.audio

import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.sync.mediaTimeUs
import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ShortBuffer

/** The ramp at each side of a splice — long enough to hide the step, too short to hear as a fade. */
private const val FADE_MS = 5

/** How far before a range's start the grabber lands, so the splice can trim to the sample. */
private const val SPLICE_PREROLL_US = 100_000L

/** The edit's sound for a frame: the tempo filter to go on with, and the frame's media time. */
internal class AudioStep(val tempo: AudioTempoFilter?, val atMs: Long?)

/**
 * The sound of a cut film, spliced to the sample (ME-233). Before this the audio loop
 * re-seeked when the picture joined and flushed the line, so every join dropped up to
 * ~300 ms of queued sound and left a step and a gap — the click the author heard.
 *
 * Now the audio loop walks the kept ranges itself, **ahead of the picture**: a frame that
 * runs past its range's end is cut there, the grabber seeks to the next range — 1–11 ms on
 * a 4K film's AC-3 (`AudioJoinProbe`, 2026-11-04), against the ~200 ms the loop decodes
 * ahead — and the next range starts at its exact sample. A [FADE_MS] ramp each side hides
 * the step; a freeze is silence for its hold, as in a render. Nothing is flushed, so the
 * line plays on straight through the join.
 *
 * Confined to the audio loop's thread, apart from [index], which the audio reseek writes.
 */
internal class AudioRanges {

    /** The range the audio is in — ahead of the picture by what the loop has queued. */
    @Volatile
    var index = 0

    /** Past the last range: nothing more to decode until a seek. */
    @Volatile
    var ended = false

    /** The speed the tempo filter in use was built for. */
    var builtSpeed = 1f

    private var fadeIn = false
    private var listGen = -1

    /** After a seek to [videoMs]: which range the sound is in, and a fresh start. */
    fun follow(player: VideoPlayerState, videoMs: Long) {
        index = indexFor(player, videoMs)
        listGen = player.edit.joins.listGen
        ended = false
        fadeIn = false
        builtSpeed = player.playbackSpeed
    }

    /** How far the sound at source [audioMs] is ahead of the picture, on the result. */
    fun aheadMs(player: VideoPlayerState, audioMs: Long): Long {
        val joins = player.edit.joins
        // An edit of the list with no seek: find the sound's range again, or it reads as far ahead.
        if (listGen != joins.listGen) {
            listGen = joins.listGen
            index = indexFor(player, audioMs + player.audioDelayMs)
            ended = false
        }
        val shown = joins.ranges.getOrNull(joins.shownIndex)
        val picture = if (shown?.isFreeze == true) startOf(joins.ranges, joins.shownIndex) + joins.heldMs
        else resultOf(joins.ranges, joins.shownIndex, player.currentPositionMs)
        return resultOf(joins.ranges, index, audioMs + player.audioDelayMs) - picture
    }

    /** [ended], unless the list changed since — a grown list left the sound silent (1.0.6 review). */
    fun parked(player: VideoPlayerState): Boolean = ended && listGen == player.edit.joins.listGen

    /** Whether a speed change is only the picture catching up with a pace the sound already has. */
    fun alreadyAt(player: VideoPlayerState): Boolean = player.playbackSpeed == builtSpeed

    /** One decoded [frame]: its part inside the range, and the splice when it runs past the end. */
    suspend fun deliver(
        player: VideoPlayerState,
        grabber: FFmpegFrameGrabber,
        frame: Frame,
        tempo: AudioTempoFilter?,
        channel: Channel<ByteArray>
    ): AudioStep {
        val buffer = frame.samples?.firstOrNull() as? ShortBuffer ?: return AudioStep(tempo, null)
        val ranges = player.edit.joins.ranges
        val range = ranges.getOrNull(index) ?: return AudioStep(tempo, null)
        val channels = grabber.audioChannels.coerceAtLeast(1)
        val rate = grabber.sampleRate
        val startUs = grabber.mediaTimeUs
        val samples = ShortArray(buffer.remaining()).also { buffer.get(it) }
        val frames = samples.size / channels
        val keepFrom = framesAt(audioUs(player, range.fromMs) - startUs, rate).coerceIn(0, frames)
        val keepTo = framesAt(audioUs(player, range.toMs) - startUs, rate).coerceIn(keepFrom, frames)
        val kept = samples.copyOfRange(keepFrom * channels, keepTo * channels)
        val fade = rate * FADE_MS / 1000
        if (fadeIn && kept.isNotEmpty()) {
            ramp(kept, channels, fade, rising = true)
            fadeIn = false
        }
        val splicing = keepTo < frames
        if (splicing) ramp(kept, channels, fade, rising = false)
        if (kept.isNotEmpty()) sendKept(frame, kept, tempo, channel)
        val atMs = startUs / 1000L
        if (!splicing) return AudioStep(tempo, atMs)
        val next = splice(player, grabber, ranges, tempo, channel)
        // **The sound is now at the next range's start**, not at this frame's time in the old
        // one: measured against range `index` that time read tens of seconds behind or ahead,
        // and the loop re-seeked or stalled at every join (1.0.6 review).
        val spliced = ranges.getOrNull(index)?.takeUnless { ended }?.let { audioUs(player, it.fromMs) / 1000L }
        return AudioStep(next, spliced ?: atMs)
    }

    /** Into the next range: a freeze's silence first, then the seek, and the next range's tempo. */
    private suspend fun splice(
        player: VideoPlayerState,
        grabber: FFmpegFrameGrabber,
        ranges: List<SourceRange>,
        tempo: AudioTempoFilter?,
        channel: Channel<ByteArray>
    ): AudioTempoFilter? {
        var next = index + 1
        while (ranges.getOrNull(next)?.isFreeze == true) {
            channel.send(ByteArray(silenceBytes(grabber, ranges[next].holdMs)))
            next++
        }
        val range = ranges.getOrNull(next) ?: return tempo.also { ended = true }
        index = next
        fadeIn = true
        runCatching { grabber.setTimestamp((audioUs(player, range.fromMs) - SPLICE_PREROLL_US).coerceAtLeast(0L)) }
        val wanted = (player.edit.userSpeed * range.speed).coerceIn(0.25f, 4f)
        // Rebuilt even at the same speed: `atempo` does not survive the seek just made, and a
        // kept one failed for good at the first backward join (`audioReseek`'s own finding).
        if (wanted == 1f && builtSpeed == 1f) return tempo
        tempo?.close()
        builtSpeed = wanted
        return if (wanted == 1f) null else AudioTempoFilter(grabber.sampleRate, grabber.audioChannels, wanted)
    }

    /** The range holding [videoMs] — the one on screen first, as a duplicated clip has two. */
    private fun indexFor(player: VideoPlayerState, videoMs: Long): Int {
        val joins = player.edit.joins
        if (joins.ranges.getOrNull(joins.shownIndex)?.holdsVideo(videoMs) == true) return joins.shownIndex
        return joins.ranges.indexOfFirst { it.holdsVideo(videoMs) }.takeIf { it >= 0 } ?: joins.decoding
    }
}
