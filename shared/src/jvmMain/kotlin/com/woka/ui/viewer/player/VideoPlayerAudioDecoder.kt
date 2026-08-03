package com.woka.ui.viewer.player

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.nio.Buffer
import java.nio.ShortBuffer
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
        while (isActive && !tracksProbed) delay(10.milliseconds)

        var grabber = openAudioGrabber(tracks.selectedAudioIndex ?: -1)
        try {
            grabber.start()
            if (grabber.audioChannels > 0) initAudio(grabber, audioChannel)

            var audioSeekGen = seekGeneration
            var audioDelayGen = audioDelayGeneration
            var audioTsMs = 0L // last decoded audio position; drives sync, unlike the
            // grabber's timestamp which is stale right after a seek.
            while (isActive) {
                val reopenIndex = pendingAudioReopenIndex
                if (reopenIndex != null) {
                    pendingAudioReopenIndex = null
                    grabber = reopenAudioGrabber(grabber, reopenIndex, audioChannel)
                    audioTsMs = audioTimeFor(currentPositionMs)
                    audioSeekGen = seekGeneration
                    continue
                }
                if (audioSeekGen != seekGeneration) {
                    audioSeekGen = seekGeneration
                    audioSeekTo(grabber, audioTimeFor(seekTargetMs), audioChannel)
                    audioTsMs = audioTimeFor(seekTargetMs)
                    continue
                }
                // The user moved the audio offset (§3): re-seek to the new target.
                // Waiting it out would work only when the offset moves forward —
                // backwards it would just idle on the lead throttle in silence.
                if (audioDelayGen != audioDelayGeneration) {
                    audioDelayGen = audioDelayGeneration
                    audioSeekTo(grabber, audioTimeFor(currentPositionMs), audioChannel)
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
                if (videoReadyGen != seekGeneration) {
                    delay(10.milliseconds)
                    continue
                }

                val aheadMs = audioTsMs - audioTimeFor(currentPositionMs)
                if (aheadMs > AUDIO_LEAD_MS) {          // decoded far enough ahead
                    delay(10.milliseconds)
                    continue
                }
                if (aheadMs < -AUDIO_RESYNC_LAG_MS) {   // fell behind (e.g. after re-open)
                    audioSeekTo(grabber, audioTimeFor(currentPositionMs), audioChannel)
                    audioTsMs = audioTimeFor(currentPositionMs)
                    continue
                }

                val frame = grabber.grabFrame(true, false, true, false, false)
                if (frame == null) {
                    delay(30.milliseconds)
                    continue
                }
                frame.samples?.let {
                    // Lazy self-heal: if the eager init was skipped (channel count
                    // came back 0 on open) but audio is actually decoding, bring the
                    // sound line up now.
                    if (soundLineInstance == null) initAudio(grabber, audioChannel)
                    audioTsMs = grabber.timestamp / 1000L
                    sendAudioSamples(it, audioChannel)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            disposeAudioLine()
            try { grabber.stop(); grabber.release() } catch (_: Exception) {}
        }
    }
}

private suspend fun sendAudioSamples(samples: Array<Buffer>, audioChannel: Channel<ByteArray>) {
    val channelSamples = samples[0] as ShortBuffer
    val shortArray = ShortArray(channelSamples.capacity())
    channelSamples.get(shortArray)
    val byteArray = ByteArray(shortArray.size * 2)
    for (i in shortArray.indices) {
        byteArray[i * 2] = shortArray[i].toByte()
        byteArray[i * 2 + 1] = (shortArray[i].toInt() shr 8).toByte()
    }
    audioChannel.send(byteArray)
}
