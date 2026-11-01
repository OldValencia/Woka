package com.woka.feature.viewer.video.engine.decode

import com.woka.core.data.MediaKind
import com.woka.core.data.RepeatMode
import com.woka.core.data.mediaKind
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.sync.lengthMs
import com.woka.feature.viewer.video.engine.writeUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import kotlin.time.Duration.Companion.milliseconds

/** A file with no picture, played on this clock rather than by the video decoder (AP-003). */
val VideoPlayerState.isAudioOnly: Boolean
    get() = file.mediaKind() == MediaKind.Audio

/** How often the clock moves the play head; the seek bar reads it at this grain. */
private const val TICK_MS = 20L

/**
 * Past the last decoded chunk's start before the file counts as over: the chunk's
 * own length plus the sound line's ~100 ms buffer, or the tail would be cut off.
 */
private const val END_TAIL_MS = 150L

/**
 * The master clock for a file with no picture (AP-003), standing where the video
 * decoder and the presenter stand for a film. The audio loop is unchanged: it was
 * always built to follow an external clock — a 200 ms lead, a 400 ms lag resync, a
 * seek gate — so this only has to be one.
 *
 * **A wall clock, scaled by the speed**, rather than samples counted at the line:
 * counting would mean rewriting the writer and its `atempo` accounting, and the
 * presenter it replaces is a wall clock too. It does not move until the audio loop
 * has fed the line under the current seek, so opening the file or a seek never
 * runs the play head ahead of the sound.
 *
 * **The video grabber is never opened.** An embedded cover is a one-picture video
 * stream (AP-001, `audio-player.md` §1a); decoded as video it would end playback
 * after its one frame.
 */
internal fun VideoPlayerState.startAudioClockLoop() {
    presenterJob = scope.launch(Dispatchers.IO) {
        FFmpegLogCallback.set()
        av_log_set_level(AV_LOG_QUIET)
        if (!probeAudioFile()) return@launch
        var seekGen = generation.seek
        var lastTick = System.nanoTime()
        // The play head in ns, so a tick's fraction of a millisecond is carried, not
        // dropped: truncated, the clock ran 2.2 % slow, the audio loop's lead throttle
        // starved the line, and a song broke up within seconds (measured 2026-11-01).
        var positionNs = currentPositionMs * 1_000_000.0
        while (isActive) {
            val now = System.nanoTime()
            val elapsedNs = now - lastTick
            lastTick = now
            if (seekGen != generation.seek) {
                seekGen = generation.seek
                currentPositionMs = generation.targetMs
                positionNs = currentPositionMs * 1_000_000.0
                generation.videoSeekComplete(seekGen) // opens the audio loop's gate
                continue
            }
            val end = generation.audioEndFor(seekGen)
            if (end != null && currentPositionMs >= end + END_TAIL_MS) {
                endOfAudio(end)
                positionNs = currentPositionMs * 1_000_000.0
            } else if (isPlaying && !isSeeking && generation.isAudioFed) {
                positionNs += elapsedNs * playbackSpeed
                currentPositionMs = (positionNs / 1_000_000.0).toLong()
            }
            delay(TICK_MS.milliseconds)
        }
    }
}

/**
 * Opens the file once, video disabled, for its length and its tracks — what the
 * video decoder does on its first open. False when the container would not open.
 */
private suspend fun VideoPlayerState.probeAudioFile(): Boolean {
    val grabber = FFmpegFrameGrabber(file)
    grabber.videoStream = Int.MAX_VALUE
    try {
        grabber.start()
        val length = grabber.lengthMs(file)
        writeUiState { durationMs = length }
        probeTracksAndChapters(grabber)
        return true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        writeUiState { openFailed = true }
        return false
    } finally {
        // Never leave the audio loop waiting on a probe that did not happen.
        tracksProbed.complete(Unit)
        try {
            grabber.stop()
            grabber.release()
        } catch (_: Exception) {}
    }
}

/**
 * The presenter's end-of-stream, for audio: Repeat File starts again, anything else
 * stops on the last moment and raises [VideoPlayerState.reachedEnd] for the playlist.
 */
private fun VideoPlayerState.endOfAudio(endMs: Long) {
    if (repeatMode == RepeatMode.File) {
        currentPositionMs = 0L
        generation.requestSeek(0L)
        return
    }
    if (!isPlaying && reachedEnd) return
    if (durationMs <= 0L) durationMs = endMs
    isPlaying = false
    currentPositionMs = durationMs
    reachedEnd = true
}
