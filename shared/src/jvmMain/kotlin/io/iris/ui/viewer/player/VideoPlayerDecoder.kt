package io.iris.ui.viewer.player

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import java.nio.ShortBuffer
import javax.sound.sampled.SourceDataLine
import kotlin.time.Duration.Companion.milliseconds

internal fun VideoPlayerState.startDecoderLoop(
    videoChannel: Channel<Pair<ImageBitmap, Long>>,
    audioChannel: Channel<ByteArray>
) {
    grabberJob = scope.launch(Dispatchers.IO) {
        FFmpegLogCallback.set()
        av_log_set_level(AV_LOG_QUIET)
        
        val grabber = FFmpegFrameGrabber(file)
        var soundLine: SourceDataLine? = null
        try {
            grabber.setVideoOption("hwaccel", "auto")
            grabber.pixelFormat = AV_PIX_FMT_RGBA
            grabber.start()
            durationMs = grabber.lengthInTime / 1000L

            val channels = grabber.audioChannels
            hasAudio = channels > 0
            
            if (hasAudio) {
                soundLine = initAudio(grabber, audioChannel)
            }

            while (isActive) {
                handleSeekIfNeeded(grabber, videoChannel, audioChannel, soundLine)

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
                            videoChannel.send(Pair(ImageBitmap(1, 1), -2L))
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
                    videoChannel.send(Pair(bitmap, frameTsMs))
                } else if (frame.samples != null && soundLine != null) {
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
            
            videoChannel.close()
            audioChannel.close()
            audioJob?.cancel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            soundLine?.stop()
            soundLine?.flush()
            soundLine?.close()
            try { 
                grabber.stop()
                grabber.release() 
            } catch (_: Exception) {}
            
            if (isActive) isPlaying = false
        }
    }
}
