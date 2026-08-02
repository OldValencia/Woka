package com.woka.ui.viewer.player

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.abs

/** A decoded video frame tagged with the seek generation it was produced under. */
internal data class VideoFrame(val bitmap: ImageBitmap, val tsMs: Long, val generation: Int, val hdrInfo: HdrInfo? = null)

internal fun VideoPlayerState.startPlayback() {
    val videoChannel = Channel<VideoFrame>(capacity = 60)
    val audioChannel = Channel<ByteArray>(capacity = 500)

    startPresenterLoop(videoChannel)
    startDecoderLoop(videoChannel)
    startAudioDecodeLoop(audioChannel)
}

internal fun VideoPlayerState.startPresenterLoop(videoChannel: Channel<VideoFrame>) {
    presenterJob = scope.launch(Dispatchers.Default) {
        var startPlayTimeMs = System.nanoTime() / 1_000_000L
        var firstVideoTsMs = -1L
        var lastPauseTimeMs = 0L

        for ((bitmap, frameTsMs, generation, hdrInfo) in videoChannel) {
            // Drop frames decoded before the latest seek so they can't overwrite
            // the post-seek position/frame with stale data.
            if (generation != seekGeneration) continue

            if (frameTsMs == -2L) {
                isPlaying = false
                currentPositionMs = durationMs
                continue
            }
            while (!isPlaying || isSeeking) {
                if (lastPauseTimeMs == 0L) lastPauseTimeMs = System.nanoTime() / 1_000_000L
                delay(50.milliseconds)
            }
            
            if (lastPauseTimeMs != 0L) {
                startPlayTimeMs += ((System.nanoTime() / 1_000_000L) - lastPauseTimeMs)
                lastPauseTimeMs = 0L
            }

            // A video-track swap restarted the decoder under us (§2.3). The time it
            // took is not playback time, so rebase rather than bill it to these
            // frames as lateness — otherwise they get dropped to "catch up" and the
            // picture stalls until the decoder claws the gap back at ~1.07x.
            if (rebasePresenterClock) {
                rebasePresenterClock = false
                firstVideoTsMs = -1L
            }

            if (firstVideoTsMs == -1L) {
                firstVideoTsMs = frameTsMs
                startPlayTimeMs = System.nanoTime() / 1_000_000L
            }

            var elapsedMs = (System.nanoTime() / 1_000_000L) - startPlayTimeMs
            var targetTimeMs = frameTsMs - firstVideoTsMs
            var sleepTime = targetTimeMs - elapsedMs

            // Discontinuity detection: if the frame is wildly out of sync (> 1000ms),
            // it means a seek occurred or the channel contained stale frames.
            if (abs(sleepTime) > 1000) {
                firstVideoTsMs = frameTsMs
                startPlayTimeMs = System.nanoTime() / 1_000_000L
                elapsedMs = 0
                targetTimeMs = 0
                sleepTime = 0
            }

            if (sleepTime > 0) {
                delay(sleepTime.milliseconds)
            } else if (sleepTime < -50) {
                // Frame is more than 50ms late, drop it to catch up with audio
                if (generation != seekGeneration) continue
                if (frameTsMs >= currentPositionMs || abs(currentPositionMs - frameTsMs) > 1000) {
                    currentPositionMs = frameTsMs
                }
                continue
            }

            // A seek may have completed while we were parked or sleeping above.
            // If so this frame is stale — dropping it here stops it from dragging
            // the play head back to the pre-seek position (visible only while
            // playing, when the presenter is mid-sleep rather than parked).
            if (generation != seekGeneration) continue

            if (frameTsMs >= currentPositionMs || abs(currentPositionMs - frameTsMs) > 1000) {
                currentPositionMs = frameTsMs
            }

            currentFrame = bitmap
            currentFrameHdrInfo = hdrInfo
        }
    }
}
