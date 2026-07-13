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

            val elapsedMs = System.currentTimeMillis() - startPlayTimeMs
            val targetTimeMs = frameTsMs - firstVideoTsMs
            
            if (kotlin.math.abs(targetTimeMs - elapsedMs) > 1000) {
                firstVideoTsMs = frameTsMs
                startPlayTimeMs = System.currentTimeMillis()
            }

            val newElapsedMs = System.currentTimeMillis() - startPlayTimeMs
            val newTargetTimeMs = frameTsMs - firstVideoTsMs
            val sleepTime = newTargetTimeMs - newElapsedMs

            if (sleepTime > 0) {
                delay(sleepTime.milliseconds)
            }

            if (frameTsMs >= currentPositionMs || kotlin.math.abs(currentPositionMs - frameTsMs) > 1000) {
                currentPositionMs = frameTsMs
            }

            currentFrame = bitmap
        }
    }
}
