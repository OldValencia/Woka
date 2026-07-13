package io.iris.ui.viewer.player

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.nio.ShortBuffer
import javax.sound.sampled.SourceDataLine
import kotlin.time.Duration.Companion.milliseconds

internal fun VideoPlayerState.startDecoderLoop(
    videoChannel: Channel<Pair<ImageBitmap, Long>>,
    audioChannel: Channel<ByteArray>
) {
    grabberJob = scope.launch(Dispatchers.IO) {
        val grabber = FFmpegFrameGrabber(file)
        var soundLine: SourceDataLine? = null
        try {
            grabber.start()
            durationMs = grabber.lengthInTime / 1000L

            val converter = Java2DFrameConverter()
            val channels = grabber.audioChannels
            hasAudio = channels > 0
            
            if (hasAudio) {
                soundLine = initAudio(grabber, audioChannel)
            }

            while (isActive) {
                handleSeekIfNeeded(grabber, converter, videoChannel, audioChannel, soundLine)

                if (!isPlaying || isSeeking) {
                    delay(50.milliseconds)
                    continue
                }

                val frame = grabber.grabFrame()
                if (frame == null) {
                    if (isRepeatMode) {
                        seekTargetMs = 0L
                    } else {
                        isPlaying = false
                    }
                    continue
                }
                val frameTsMs = grabber.timestamp / 1000L

                if (frame.image != null) {
                    val bufferedImage = converter.getBufferedImage(frame)
                    val bitmap = bufferedImage.toComposeBitmap()
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
