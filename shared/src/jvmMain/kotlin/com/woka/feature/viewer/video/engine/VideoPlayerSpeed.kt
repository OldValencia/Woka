package com.woka.feature.viewer.video.engine

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.PlaybackSpeed
import com.woka.core.data.RepeatMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.bytedeco.javacv.FFmpegFrameGrabber
import kotlin.time.Duration.Companion.milliseconds

/**
 * Playback speed and repeat (§6).
 *
 * Speed is a presentation decision, not a decode one: the decoder keeps producing
 * frames at their natural timestamps, and only the pacing scales. Audio is the
 * exception — see [AudioTempoFilter].
 */

fun VideoPlayerState.setPlaybackSpeed(speed: Float) {
    val snapped = PlaybackSpeed.snap(speed)
    if (snapped == playbackSpeed) return
    playbackSpeed = snapped
    speedGeneration++
    // The presenter's baseline was anchored under the old speed; without a rebase
    // every incoming frame would read as wildly early or late right after the
    // change and get dropped or slept on.
    rebasePresenterClock = true
}

/**
 * End of stream. Only Repeat File loops here — Repeat Folder means "play the next
 * file", which is navigation, so the decoder emits the EOF sentinel instead and
 * lets the playlist layer take over (§6).
 */
internal suspend fun VideoPlayerState.handleEndOfStream(
    videoChannel: Channel<VideoFrame>,
    frameGeneration: Int
) {
    if (repeatMode == RepeatMode.File) {
        seekTargetMs = 0L
        // Move the play head BEFORE bumping the generation. The audio loop measures
        // itself against currentPositionMs; left at the end it read as ~a whole file
        // behind, re-seeked its own grabber back to the end, and then sat out the
        // entire replay on the lead throttle — a silent second pass.
        currentPositionMs = 0L
        seekGeneration++ // both loops restart from the beginning
        return
    }
    videoChannel.send(VideoFrame(ImageBitmap(1, 1), -2L, frameGeneration))
    val gen = seekGeneration
    while (isPlaying && seekGeneration == gen && scope.isActive) {
        delay(50.milliseconds)
    }
}

/** Clears the EOF latch — any seek or resume means playback is live again (§6). */
internal fun VideoPlayerState.clearReachedEnd() {
    if (reachedEnd) reachedEnd = false
}

/**
 * The tempo filter for the current speed, or null at 1x (the untouched fast
 * path). Built from the live grabber, so it always matches the track actually
 * playing. Reading [playbackSpeed] here — rather than only reacting to a change —
 * is what makes a file opened while 2x is already selected come up filtered.
 */
internal fun VideoPlayerState.buildTempoFilter(grabber: FFmpegFrameGrabber): AudioTempoFilter? {
    if (playbackSpeed == PlaybackSpeed.NORMAL || grabber.audioChannels <= 0) return null
    return AudioTempoFilter(grabber.sampleRate, grabber.audioChannels, playbackSpeed)
}

/**
 * Seeks the audio grabber and hands back a *fresh* tempo filter. Every seek
 * restarts the stream, so the next frame carries a timestamp behind the one
 * already fed to `atempo`; the graph rejects the backwards jump and then fails
 * for good, which silently dropped the sound back to 1x after a loop or a skip.
 * Throwing the graph away on each discontinuity is cheaper than diagnosing it.
 */
internal fun VideoPlayerState.audioReseek(
    grabber: FFmpegFrameGrabber,
    targetMs: Long,
    audioChannel: Channel<ByteArray>,
    tempo: AudioTempoFilter?
): AudioTempoFilter? {
    audioSeekTo(grabber, targetMs, audioChannel)
    tempo?.close()
    return buildTempoFilter(grabber)
}
