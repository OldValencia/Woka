package com.woka.ui.viewer.player

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import java.nio.ShortBuffer
import kotlin.time.Duration.Companion.milliseconds

internal fun VideoPlayerState.startDecoderLoop(
    videoChannel: Channel<VideoFrame>,
    audioChannel: Channel<ByteArray>
) {
    grabberJob = scope.launch(Dispatchers.IO) {
        FFmpegLogCallback.set()
        av_log_set_level(AV_LOG_QUIET)
        
        val grabber = FFmpegFrameGrabber(file)
        try {
            grabber.setVideoOption("hwaccel", "auto")
            grabber.pixelFormat = AV_PIX_FMT_RGBA
            grabber.start()
            durationMs = grabber.lengthInTime / 1000L

            if (grabber.audioChannels > 0) {
                hasAudio = true
                initAudio(grabber, audioChannel)
            }

            // Tag every frame with the seek generation it was decoded under. It
            // only advances once a pending seek has actually been processed, so
            // frames grabbed before the seek keep the old generation and get
            // discarded by the presenter.
            var frameGeneration = seekGeneration

            while (isActive) {
                val seekedGeneration = handleSeekIfNeeded(grabber, videoChannel, audioChannel)
                if (seekedGeneration != -1) frameGeneration = seekedGeneration

                if (!isPlaying || isSeeking) {
                    delay(50.milliseconds)
                    continue
                }

                val frame = grabber.grabFrame()
                if (frame == null) {
                    if (isRepeatMode) {
                        seekTargetMs = 0L
                    } else {
                        if (hasAudio) {
                            audioChannel.send(ByteArray(0))
                        } else {
                            videoChannel.send(VideoFrame(ImageBitmap(1, 1), -2L, frameGeneration))
                        }
                        while (isPlaying && seekTargetMs == -1L && isActive) {
                            delay(50.milliseconds)
                        }
                    }
                    continue
                }
                val frameTsMs = grabber.timestamp / 1000L

                if (frame.image != null) {
                    val bitmap = frame.toComposeBitmap()
                    videoChannel.send(VideoFrame(bitmap, frameTsMs, frameGeneration))
                } else if (frame.samples != null) {
                    if (soundLineInstance == null) {
                        hasAudio = true
                        initAudio(grabber, audioChannel)
                    }
                    if (soundLineInstance != null) {
                        val channelSamples = frame.samples[0] as ShortBuffer
                    val shortArray = ShortArray(channelSamples.capacity())
                    channelSamples.get(shortArray)
                    
                    val byteArray = ByteArray(shortArray.size * 2)
                    for (i in shortArray.indices) {
                        byteArray[i * 2] = shortArray[i].toByte()
                        byteArray[i * 2 + 1] = (shortArray[i].toInt() shr 8).toByte()
                    }
                    
                    audioChannel.send(byteArray)
                    }
                }
            } // Close while (isActive)
            
            videoChannel.close()
            audioChannel.close()
            audioJob?.cancel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            soundLineInstance?.stop()
            soundLineInstance?.flush()
            soundLineInstance?.close()
            soundLineInstance = null
            try { 
                grabber.stop()
                grabber.release() 
            } catch (_: Exception) {}
            
            if (isActive) isPlaying = false
        }
    }
}
