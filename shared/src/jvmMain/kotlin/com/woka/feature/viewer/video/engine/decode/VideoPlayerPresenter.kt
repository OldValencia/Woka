package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.VideoPlayerState

import com.woka.feature.viewer.video.engine.recordPresentedFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.abs

// Consecutive drops after which the presenter re-anchors instead of staying in the
// band where nothing is late enough to rebase but everything is late enough to drop.
private const val MAX_DROPS_IN_A_ROW = 10

/** The frame period a join is scheduled by, until two frames have shown the film's own. */
private const val DEFAULT_STEP_MS = 40L

internal fun VideoPlayerState.startPresenterLoop(videoChannel: Channel<VideoFrame>) {
    presenterJob = scope.launch(Dispatchers.Default) {
        var startPlayTimeMs = System.nanoTime() / 1_000_000L
        var firstVideoTsMs = -1L
        var lastPauseTimeMs = 0L
        var droppedInARow = 0
        var stepMs = DEFAULT_STEP_MS
        var lastShownTsMs = -1L

        for ((bitmap, frameTsMs, frameGeneration, hdrInfo, frameId, joinIndex, heldFromMs) in videoChannel) {
            // Drop frames decoded before the latest seek so they can't overwrite
            // the post-seek position/frame with stale data.
            if (frameGeneration != generation.seek) continue

            if (frameTsMs == -2L) {
                isPlaying = false
                currentPositionMs = durationMs
                reachedEnd = true // the playlist layer decides what plays next (§6)
                continue
            }
            if (frameTsMs == END_OF_EDIT_TS) {
                stopAtEditEnd()
                continue
            }
            while (!isPlaying || isSeeking) {
                // Nothing is late while nothing is playing (§health).
                health.suspendMeasuring()
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
            if (joinIndex >= 0) paceJoin(joinIndex)
            if (generation.consumePresenterRebase()) {
                firstVideoTsMs = -1L
                // A grabber swap is not the machine struggling (§health).
                health.suspendMeasuring()
            }

            // A join (ME-232) is due one frame after the last shown, not now — and its
            // timestamps jump, so it starts a schedule of its own.
            if (joinIndex >= 0) {
                firstVideoTsMs = frameTsMs
                startPlayTimeMs = System.nanoTime() / 1_000_000L + (stepMs / playbackSpeed).toLong()
            }
            if (firstVideoTsMs == -1L) {
                firstVideoTsMs = frameTsMs
                startPlayTimeMs = System.nanoTime() / 1_000_000L
                // Rebasing means the clock just restarted — a seek, a swap, or the
                // first frame of the file. The decoder is priming, and every file
                // looks slow there, so the count starts again from here (§health).
                health.suspendMeasuring()
            }

            var elapsedMs = (System.nanoTime() / 1_000_000L) - startPlayTimeMs
            // Media time is divided by the speed to get wall time: at 2x a frame
            // 1000ms into the stream is due after 500ms of real time (§6).
            var targetTimeMs = ((frameTsMs - firstVideoTsMs) / playbackSpeed).toLong()
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
                // A stall — a long GC, a burst of slow conversions — leaves the
                // baseline behind by more than the drop threshold but less than the
                // 1s discontinuity one. That dead band drops every frame forever and
                // the picture never comes back, so a run of drops rebases too.
                if (++droppedInARow >= MAX_DROPS_IN_A_ROW) firstVideoTsMs = -1L
                // Sustained drops are the other face of "not keeping up"; background
                // work such as seek-bar previews stands down on it (§preview).
                if (droppedInARow >= 3) generation.isBehind = true
                // Frame is more than 50ms late, drop it to catch up with audio
                if (frameGeneration != generation.seek) continue
                if (frameTsMs >= currentPositionMs || abs(currentPositionMs - frameTsMs) > 1000) {
                    currentPositionMs = frameTsMs
                }
                continue
            }

            // A seek may have completed while we were parked or sleeping above.
            // If so this frame is stale — dropping it here stops it from dragging
            // the play head back to the pre-seek position (visible only while
            // playing, when the presenter is mid-sleep rather than parked).
            if (frameGeneration != generation.seek) continue

            if (frameTsMs >= currentPositionMs || abs(currentPositionMs - frameTsMs) > 1000) {
                currentPositionMs = frameTsMs
            }

            droppedInARow = 0
            generation.isBehind = false
            currentFrame = bitmap
            showColour(hdrInfo)
            currentFrameId = frameId
            edit.shownMs = frameTsMs
            recordPresentedFrame() // the one hot-path cost of §health
            if (joinIndex >= 0) {
                presentJoin(joinIndex, heldFromMs, frameTsMs)
                if (isFreezeRange(joinIndex)) holdFreeze(joinIndex, heldFromMs, frameGeneration)
            } else if (frameTsMs > lastShownTsMs && lastShownTsMs >= 0) {
                stepMs = (frameTsMs - lastShownTsMs).coerceIn(1L, DEFAULT_STEP_MS * 3)
            }
            lastShownTsMs = frameTsMs
        }
    }
}
