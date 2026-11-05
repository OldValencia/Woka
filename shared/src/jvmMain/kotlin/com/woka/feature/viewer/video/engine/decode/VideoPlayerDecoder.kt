package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.launchResumeSaver
import com.woka.feature.viewer.video.engine.resumePositionMs
import com.woka.feature.viewer.video.engine.saveResumePosition
import com.woka.feature.viewer.video.engine.seek
import com.woka.feature.viewer.video.engine.sync.videoSeekTo
import com.woka.feature.viewer.video.engine.track.reopenVideoGrabber

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegLogCallback
import kotlin.time.Duration.Companion.milliseconds
import com.woka.feature.viewer.video.engine.handleEndOfStream
import com.woka.feature.viewer.video.engine.writeUiState
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import com.woka.feature.viewer.video.engine.probe.keptOn
import com.woka.feature.viewer.video.engine.join.JoinStep

/**
 * Decodes ONLY the video stream (audio runs on its own grabber, see
 * startAudioDecodeLoop). This is the master timeline: the presenter derives
 * currentPositionMs from these frames and the audio loop follows it.
 */
internal fun VideoPlayerState.startDecoderLoop(videoChannel: Channel<VideoFrame>) {
    grabberJob = scope.launch(Dispatchers.IO) {
        FFmpegLogCallback.set()
        av_log_set_level(AV_LOG_QUIET)

        // Reassigned when the user switches video track (§2.3); `finally` always
        // tears down whichever grabber is current.
        var grabber = openVideoGrabber()
        val pacer = FramePacer(frameRetention)
        // Kept ranges played without a seek at each join (ME-232); idle with none set.
        val joins = JoinStep(this@startDecoderLoop, videoChannel, pacer)
        try {
            var hdrInfo = startVideoGrabber(grabber)
            probeTracksAndChapters(grabber)

            // Frames carry the generation they were decoded under; the presenter
            // discards stale ones. videoSeekGen tracks the last seek we applied.
            var frameGeneration = generation.seek
            var videoSeekGen = generation.seek
            var durationChecked = durationMs > 0L

            // Resume (§4) goes through the ordinary seek path, and only after
            // videoSeekGen has been captured — set before it, the generation bump
            // would already be accounted for and the loop would never act on it.
            resumePositionMs()?.let { seek(it) }
            launchResumeSaver(this)

            while (isActive) {
                val reopenIndex = pendingVideoReopenIndex
                if (reopenIndex != null) {
                    pendingVideoReopenIndex = null
                    val stream = grabber.videoStream // a rescale keeps the film's white point (1.0.6 review)
                    grabber = reopenVideoGrabber(grabber, reopenIndex, videoChannel)
                    hdrInfo = grabber.readStreamHdrInfo()
                    writeUiState { streamHdrInfo = hdrInfo.takeIf { grabber.videoStream == stream }?.keptOn(streamHdrInfo) ?: hdrInfo }
                    // The play head did not move, so no generation bump: the frame
                    // reopenVideoGrabber landed stays current and audio — parked on
                    // isReloadingVideo meanwhile — picks up from the same position.
                    frameGeneration = generation.seek
                    generation.requestPresenterRebase()
                    isReloadingVideo = false
                    pacer.rebase()
                    continue
                }
                if (videoSeekGen != generation.seek) {
                    videoSeekGen = generation.seek
                    videoSeekTo(grabber, generation.targetMs, videoChannel, hdrInfo)
                    frameGeneration = generation.seek
                    generation.videoSeekComplete(videoSeekGen) // frame landed; audio may resume
                    joins.afterSeek(generation.targetMs)
                    pacer.rebase()
                    continue
                }
                if (requestRescaleIfNeeded(grabber.videoStream)) continue

                if (joins.idle()) {
                    pacer.rebase()
                    delay(20.milliseconds)
                    continue
                }
                if (!isPlaying || isSeeking) {
                    pacer.rebase()
                    delay(50.milliseconds)
                    continue
                }

                joins.beforeGrab(grabber, frameGeneration, hdrInfo)?.let {
                    grabber = it
                    continue
                }
                val frame = grabber.grabFrame(false, true, true, false, false)
                if (frame == null && joins.active) {
                    grabber = joins.pastEnd(grabber, frameGeneration, hdrInfo)
                    continue
                }
                if (frame == null) {
                    // The picture `bwdif` held back for a neighbour that never came.
                    pictureFilter.drain()?.let {
                        deliverPicture(grabber, it, pacer, videoChannel, frameGeneration, hdrInfo)
                    }
                    handleEndOfStream(videoChannel, frameGeneration)
                    continue
                }

                if (frame.image != null) {
                    // Duration is occasionally 0 straight after start(); it's known
                    // once packets have been read, so re-read it on the first frame.
                    if (!durationChecked) {
                        if (durationMs <= 0L) durationMs = grabber.lengthInTime / 1000L
                        durationChecked = true
                    }
                    val picture = pictureOf(grabber, frame) ?: continue
                    val atMs = pictureTimeUs(grabber, picture) / 1000L
                    joins.step(grabber, picture, atMs, frameGeneration, hdrInfo)?.let {
                        grabber = it
                        continue
                    }
                    deliverPicture(grabber, picture, pacer, videoChannel, frameGeneration, hdrInfo)
                }
            }

            videoChannel.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Runs on cancellation too, which is what makes dispose() the moment a
            // closed file's position is written (§4).
            saveResumePosition()
            withContext(NonCancellable) { joins.close() }
            tracksProbed.complete(Unit) // never leave the audio loop waiting if start() failed
            isReloadingVideo = false // a failed swap must not leave a stuck spinner
            pictureFilter.close()
            try {
                grabber.stop()
                grabber.release()
            } catch (_: Exception) {}

            if (isActive) isPlaying = false
        }
    }
}
