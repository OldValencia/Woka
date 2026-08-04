package com.woka.ui.viewer.player

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegLogCallback
import kotlin.time.Duration.Companion.milliseconds

/**
 * Decodes ONLY the video stream (audio runs on its own grabber, see
 * [startAudioDecodeLoop]). This is the master timeline: the presenter derives
 * currentPositionMs from these frames and the audio loop follows it.
 */
internal fun VideoPlayerState.startDecoderLoop(videoChannel: Channel<VideoFrame>) {
    grabberJob = scope.launch(Dispatchers.IO) {
        FFmpegLogCallback.set()
        av_log_set_level(AV_LOG_QUIET)

        // Reassigned when the user switches video track (§2.3); `finally` always
        // tears down whichever grabber is current.
        var grabber = openVideoGrabber()
        try {
            grabber.start()

            var hdrInfo = grabber.readStreamHdrInfo()
            streamHdrInfo = hdrInfo
            durationMs = grabber.lengthInTime / 1000L

            try {
                // Restore the tracks and sync offsets this file was last watched
                // with, before anything downstream reads a selection (§2.6 / §3).
                applyTrackPrefs(probeMediaTracks(grabber))
                logMediaTracks(tracks.mediaTracks)
                renderScale.useNativeOf(
                    tracks.mediaTracks.video.firstOrNull { it.index == tracks.selectedVideoIndex }
                )
                // FFmpeg's own pick for the default video stream is not necessarily
                // the one flagged AV_DISPOSITION_DEFAULT, so on a multi-video
                // container the picture could come up on a different track than the
                // menu reports as selected. Swap onto the track we resolved (§2.3);
                // the play head is still 0, so this costs an open and no re-decode.
                tracks.selectedVideoIndex
                    ?.takeIf { it != grabber.videoStream }
                    ?.let { pendingVideoReopenIndex = it }
                startSubtitleDecoderLoop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // hasAudio comes from the authoritative full probe, not the audio
            // grabber's transient channel count. Then release the audio loop.
            hasAudio = tracks.mediaTracks.audio.isNotEmpty()
            tracksProbed = true

            // Frames carry the generation they were decoded under; the presenter
            // discards stale ones. videoSeekGen tracks the last seek we applied.
            var frameGeneration = seekGeneration
            var videoSeekGen = seekGeneration
            var durationChecked = durationMs > 0L
            val pacer = FramePacer()

            // Resume (§4) goes through the ordinary seek path, and only after
            // videoSeekGen has been captured — set before it, the generation bump
            // would already be accounted for and the loop would never act on it.
            resumePositionMs()?.let { seek(it) }
            launchResumeSaver(this)

            while (isActive) {
                val reopenIndex = pendingVideoReopenIndex
                if (reopenIndex != null) {
                    pendingVideoReopenIndex = null
                    grabber = reopenVideoGrabber(grabber, reopenIndex, videoChannel)
                    hdrInfo = grabber.readStreamHdrInfo()
                    streamHdrInfo = hdrInfo
                    // The play head did not move, so no generation bump: the frame
                    // reopenVideoGrabber landed stays current and audio — parked on
                    // isReloadingVideo meanwhile — picks up from the same position.
                    frameGeneration = seekGeneration
                    rebasePresenterClock = true
                    isReloadingVideo = false
                    pacer.rebase()
                    continue
                }
                if (videoSeekGen != seekGeneration) {
                    videoSeekGen = seekGeneration
                    videoSeekTo(grabber, seekTargetMs, videoChannel, hdrInfo)
                    frameGeneration = seekGeneration
                    videoReadyGen = videoSeekGen // frame has landed; audio may resume
                    pacer.rebase()
                    continue
                }
                if (requestRescaleIfNeeded(grabber.videoStream)) continue

                if (!isPlaying || isSeeking) {
                    pacer.rebase()
                    delay(50.milliseconds)
                    continue
                }

                val frame = grabber.grabFrame(false, true, true, false, false)
                if (frame == null) {
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
                    val frameTsMs = grabber.timestamp / 1000L
                    // Already past its slot: decoded (the stream needs it) but not
                    // converted, which is where the time actually goes (§6).
                    if (pacer.shouldSkip(frameTsMs, playbackSpeed)) continue
                    val bitmap = pacer.convert(frame)
                    videoChannel.send(VideoFrame(bitmap, frameTsMs, frameGeneration, hdrInfo))
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
            tracksProbed = true // never leave the audio loop waiting if start() failed
            isReloadingVideo = false // a failed swap must not leave a stuck spinner
            try {
                grabber.stop()
                grabber.release()
            } catch (_: Exception) {}

            if (isActive) isPlaying = false
        }
    }
}
