package io.iris.ui.viewer.player

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration.Companion.milliseconds

internal fun VideoPlayerState.startPlayback() {
    val videoChannel = Channel<Pair<ImageBitmap, Long>>(capacity = 60)
    val audioChannel = Channel<ByteArray>(capacity = 500)

    startPresenterLoop(videoChannel)
    startDecoderLoop(videoChannel, audioChannel)
}

internal fun VideoPlayerState.startPresenterLoop(videoChannel: Channel<Pair<ImageBitmap, Long>>) {
    presenterJob = scope.launch(Dispatchers.Default) {
        var startPlayTimeMs = System.currentTimeMillis()
        var firstVideoTsMs = -1L
        var lastPauseTimeMs = 0L

        for ((bitmap, frameTsMs) in videoChannel) {
            if (frameTsMs == -2L) {
                isPlaying = false
                currentPositionMs = durationMs
                continue
            }
            while (!isPlaying || isSeeking) {
                if (lastPauseTimeMs == 0L) lastPauseTimeMs = System.currentTimeMillis()
                delay(50.milliseconds)
            }
            
            if (lastPauseTimeMs != 0L) {
                startPlayTimeMs += (System.currentTimeMillis() - lastPauseTimeMs)
                lastPauseTimeMs = 0L
            }

            if (firstVideoTsMs == -1L) {
                firstVideoTsMs = frameTsMs
                startPlayTimeMs = System.currentTimeMillis()
            }

            var elapsedMs = System.currentTimeMillis() - startPlayTimeMs
            var targetTimeMs = frameTsMs - firstVideoTsMs
            var sleepTime = targetTimeMs - elapsedMs

            // Discontinuity detection: if the frame is wildly out of sync (> 1000ms),
            // it means a seek occurred or the channel contained stale frames.
            if (kotlin.math.abs(sleepTime) > 1000) {
                firstVideoTsMs = frameTsMs
                startPlayTimeMs = System.currentTimeMillis()
                elapsedMs = 0
                targetTimeMs = 0
                sleepTime = 0
            }

            if (sleepTime > 0) {
                delay(sleepTime.milliseconds)
            } else if (sleepTime < -50) {
                // Frame is more than 50ms late, drop it to catch up with audio
                if (frameTsMs >= currentPositionMs || kotlin.math.abs(currentPositionMs - frameTsMs) > 1000) {
                    currentPositionMs = frameTsMs
                }
                continue
            }

            if (frameTsMs >= currentPositionMs || kotlin.math.abs(currentPositionMs - frameTsMs) > 1000) {
                currentPositionMs = frameTsMs
            }

            currentFrame = bitmap
        }
    }
}
