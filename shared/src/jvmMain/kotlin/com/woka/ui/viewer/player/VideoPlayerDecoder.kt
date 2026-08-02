package com.woka.ui.viewer.player

import androidx.compose.ui.graphics.ImageBitmap
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

        val grabber = openVideoGrabber()
        try {
            grabber.start()

            val fc = grabber.formatContext
            val vs = fc.streams(grabber.videoStream)
            val codecpar = vs.codecpar()
            val hdrInfo = readHdrInfo(codecpar.color_trc(), codecpar.color_primaries())
            durationMs = grabber.lengthInTime / 1000L

            try {
                tracks.apply(probeMediaTracks(grabber))
                logMediaTracks(tracks.mediaTracks)
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

            while (isActive) {
                if (videoSeekGen != seekGeneration) {
                    videoSeekGen = seekGeneration
                    videoSeekTo(grabber, seekTargetMs, videoChannel)
                    frameGeneration = seekGeneration
                    videoReadyGen = videoSeekGen // frame has landed; audio may resume
                    continue
                }

                if (!isPlaying || isSeeking) {
                    delay(50.milliseconds)
                    continue
                }

                val frame = grabber.grabFrame(false, true, true, false, false)
                if (frame == null) {
                    if (isRepeatMode) {
                        seekTargetMs = 0L
                        seekGeneration++ // both loops restart from the beginning
                    } else {
                        videoChannel.send(VideoFrame(ImageBitmap(1, 1), -2L, frameGeneration))
                        val gen = seekGeneration
                        while (isPlaying && seekGeneration == gen && isActive) {
                            delay(50.milliseconds)
                        }
                    }
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
                    val bitmap = frame.toComposeBitmap(hdrInfo)
                    videoChannel.send(VideoFrame(bitmap, frameTsMs, frameGeneration, hdrInfo))
                }
            }

            videoChannel.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tracksProbed = true // never leave the audio loop waiting if start() failed
            try {
                grabber.stop()
                grabber.release()
            } catch (_: Exception) {}

            if (isActive) isPlaying = false
        }
    }
}
