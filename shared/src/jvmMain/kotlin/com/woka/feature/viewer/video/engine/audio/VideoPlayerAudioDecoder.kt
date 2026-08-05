package com.woka.feature.viewer.video.engine.audio

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.audioReseek
import com.woka.feature.viewer.video.engine.buildTempoFilter
import com.woka.feature.viewer.video.engine.decode.disposeAudioLine
import com.woka.feature.viewer.video.engine.decode.openAudioGrabber
import com.woka.feature.viewer.video.engine.decode.reopenAudioGrabber
import com.woka.feature.viewer.video.engine.sync.audioTimeFor

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration.Companion.milliseconds

// How far ahead of the video clock the audio may decode before it waits, and how
// far behind it may fall before it re-seeks to catch up.
private const val AUDIO_LEAD_MS = 200L
private const val AUDIO_RESYNC_LAG_MS = 400L

/**
 * Decodes ONLY the selected audio stream on its own grabber (§2.1) so switching
 * tracks never disturbs the video pipeline. Stays locked to the video master
 * clock (currentPositionMs): re-seeks on every generation bump (user seek /
 * repeat) and nudges forward if it falls behind after a track re-open.
 */
internal fun VideoPlayerState.startAudioDecodeLoop(audioChannel: Channel<ByteArray>) {
    audioDecodeJob = scope.launch(Dispatchers.IO) {
        // Wait for the video grabber to open + probe first, so the two grabbers
        // never do a concurrent first-open (which returned incomplete info). By
        // now hasAudio and the selected track index are set from the probe.
        tracksProbed.await()
        // A file with no audio stream at all has nothing to open. The loop below
        // already parks on !hasAudio, but the grabber is started before it is ever
        // reached — and this grabber has video disabled, so with no audio stream
        // either there is nothing left for it to find and start() throws.
        if (!hasAudio) return@launch

        var grabber = openAudioGrabber(tracks.selectedAudioIndex ?: -1)
        // Non-null only while the speed is off 1x; at 1x audio takes the original
        // path untouched (§6).
        var tempo: AudioTempoFilter? = null
        try {
            grabber.start()
            if (grabber.audioChannels > 0) initAudio(grabber, audioChannel)
            // Built from the live speed, not from a later change: reopening a file
            // while 2x is already selected bumps no generation, so a change-driven
            // build would leave the sound running at 1x under a 2x picture.
            tempo = buildTempoFilter(grabber)

            var audioSeekGen = generation.seek
            var audioDelayGen = generation.audioDelay
            var audioSpeedGen = generation.speed
            var audioTsMs = 0L // last decoded audio position; drives sync, unlike the
            // grabber's timestamp which is stale right after a seek.
            while (isActive) {
                val reopenIndex = pendingAudioReopenIndex
                if (reopenIndex != null) {
                    pendingAudioReopenIndex = null
                    grabber = reopenAudioGrabber(grabber, reopenIndex, audioChannel)
                    tempo?.close()
                    tempo = buildTempoFilter(grabber)
                    audioTsMs = audioTimeFor(currentPositionMs)
                    audioSeekGen = generation.seek
                    continue
                }
                // Every branch below goes through audioReseek, which rebuilds the
                // tempo graph: atempo cannot survive the backwards timestamp jump a
                // seek produces and would fail over to unfiltered 1x sound (§6).
                if (audioSeekGen != generation.seek) {
                    audioSeekGen = generation.seek
                    val target = audioTimeFor(generation.targetMs)
                    tempo = audioReseek(grabber, target, audioChannel, tempo)
                    audioTsMs = target
                    continue
                }
                // The user moved the audio offset (§3): re-seek to the new target.
                // Waiting it out would work only when the offset moves forward —
                // backwards it would just idle on the lead throttle in silence.
                if (audioDelayGen != generation.audioDelay) {
                    audioDelayGen = generation.audioDelay
                    tempo = audioReseek(grabber, audioTimeFor(currentPositionMs), audioChannel, tempo)
                    audioTsMs = audioTimeFor(currentPositionMs)
                    continue
                }
                // Speed changed: the queued audio was resampled at the old rate, so
                // it has to go along with the graph that produced it.
                if (audioSpeedGen != generation.speed) {
                    audioSpeedGen = generation.speed
                    tempo = audioReseek(grabber, audioTimeFor(currentPositionMs), audioChannel, tempo)
                    audioTsMs = audioTimeFor(currentPositionMs)
                    continue
                }
                // isReloadingVideo: a video-track swap re-decodes for seconds (§2.3).
                // Park instead of playing on, so sound doesn't run away from the
                // frozen picture and then need a resync once the picture returns.
                if (!isPlaying || isSeeking || isReloadingVideo || !hasAudio) {
                    delay(20.milliseconds)
                    continue
                }
                // Wait until the video has finished seeking to the current target,
                // so audio never starts before the frame lands (slider scrubs).
                if (!generation.isVideoAtSeekTarget) {
                    delay(10.milliseconds)
                    continue
                }

                val aheadMs = audioTsMs - audioTimeFor(currentPositionMs)
                if (aheadMs > AUDIO_LEAD_MS) {          // decoded far enough ahead
                    delay(10.milliseconds)
                    continue
                }
                if (aheadMs < -AUDIO_RESYNC_LAG_MS) {   // fell behind (e.g. after re-open)
                    tempo = audioReseek(grabber, audioTimeFor(currentPositionMs), audioChannel, tempo)
                    audioTsMs = audioTimeFor(currentPositionMs)
                    continue
                }

                val frame = grabber.grabFrame(true, false, true, false, false)
                if (frame == null) {
                    delay(30.milliseconds)
                    continue
                }
                frame.samples?.let { samples ->
                    // Lazy self-heal: if the eager init was skipped (channel count
                    // came back 0 on open) but audio is actually decoding, bring the
                    // sound line up now.
                    if (soundLineInstance == null) initAudio(grabber, audioChannel)
                    // Media time, unaffected by atempo — the sync comparisons above
                    // stay in the same units at every speed.
                    audioTsMs = grabber.timestamp / 1000L
                    val chunks = tempo?.process(frame)
                    if (chunks != null) {
                        chunks.forEach { audioChannel.send(it) }
                    } else {
                        sendAudioSamples(samples, audioChannel)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempo?.close()
            disposeAudioLine()
            try { grabber.stop(); grabber.release() } catch (_: Exception) {}
        }
    }
}
