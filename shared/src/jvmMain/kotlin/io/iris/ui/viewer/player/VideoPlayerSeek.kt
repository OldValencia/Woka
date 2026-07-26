package io.iris.ui.viewer.player

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import org.bytedeco.javacv.FFmpegFrameGrabber
import kotlin.math.max

/**
 * Processes a pending seek, if any. Returns the seek generation that was just
 * applied so the caller can tag subsequent frames with it, or -1 when there was
 * nothing to seek.
 */
internal fun VideoPlayerState.handleSeekIfNeeded(
    grabber: FFmpegFrameGrabber,
    videoChannel: Channel<VideoFrame>,
    audioChannel: Channel<ByteArray>
): Int {
    if (seekTargetMs != -1L) {
        val generation = seekGeneration
        val target = seekTargetMs
        seekTargetMs = -1L
        
        if (target == 0L) {
            try { grabber.restart() } catch (_: Exception) {}
            
            while (videoChannel.tryReceive().isSuccess) {}
            while (audioChannel.tryReceive().isSuccess) {}
            soundLineInstance?.flush()
            
            val audioBuffer = mutableListOf<ByteArray>()
            while (scope.isActive) {
                val frame = grabber.grabFrame() ?: break
                if (frame.image != null) {
                    currentFrame = frame.toComposeBitmap()
                    break
                } else if (frame.samples != null && soundLineInstance != null) {
                    val channelSamples = frame.samples[0] as java.nio.ShortBuffer
                    val shortArray = ShortArray(channelSamples.capacity())
                    channelSamples.get(shortArray)
                    val byteArray = ByteArray(shortArray.size * 2)
                    for (i in shortArray.indices) {
                        byteArray[i * 2] = shortArray[i].toByte()
                        byteArray[i * 2 + 1] = (shortArray[i].toInt() shr 8).toByte()
                    }
                    audioBuffer.add(byteArray)
                }
            }
            
            for (buf in audioBuffer) {
                audioChannel.trySend(buf)
            }
        } else {
            var attempt = 0
            // Seeking exactly to the end lands past the last decodable frame on
            // many containers, so grabFrame() returns null right away and no
            // frame is ever shown. Start a little earlier when the target is at
            // the very end; the loop below still decodes forward and keeps the
            // last frame it sees.
            val seekBackoffMs = if (durationMs > 0 && target >= durationMs - 1000L) 1000L else 0L
            var seekTime = max(0L, target - seekBackoffMs) * 1000L
            while (attempt < 4) {
                try { grabber.setTimestamp(seekTime) } catch (_: Exception) {}
                if (grabber.timestamp <= target * 1000L + 100000L) { // Allow 100ms tolerance
                    break
                } else {
                    seekTime = max(0L, seekTime - 2000000L)
                    attempt++
                }
            }
            if (grabber.timestamp > target * 1000L + 100000L) {
                try { grabber.restart() } catch (_: Exception) {}
            }
            
            while (videoChannel.tryReceive().isSuccess) {}
            while (audioChannel.tryReceive().isSuccess) {}
            soundLineInstance?.flush()
            
            var lastGoodFrame: org.bytedeco.javacv.Frame? = null
            val audioBuffer = mutableListOf<ByteArray>()
            
            while (scope.isActive) {
                val frame = grabber.grabFrame() ?: break
                if (grabber.timestamp >= target * 1000L) {
                    if (frame.image != null) {
                        lastGoodFrame = frame.clone()
                        break
                    } else if (frame.samples != null && soundLineInstance != null) {
                        val channelSamples = frame.samples[0] as java.nio.ShortBuffer
                        val shortArray = ShortArray(channelSamples.capacity())
                        channelSamples.get(shortArray)
                        val byteArray = ByteArray(shortArray.size * 2)
                        for (i in shortArray.indices) {
                            byteArray[i * 2] = shortArray[i].toByte()
                            byteArray[i * 2 + 1] = (shortArray[i].toInt() shr 8).toByte()
                        }
                        audioBuffer.add(byteArray)
                    }
                } else {
                    // Frame is before target, just keep searching
                    if (frame.image != null) {
                        lastGoodFrame?.close()
                        lastGoodFrame = frame.clone()
                    }
                }
            }
            if (lastGoodFrame != null && lastGoodFrame.image != null) {
                currentFrame = lastGoodFrame.toComposeBitmap()
                lastGoodFrame.close()
            }
            
            for (buf in audioBuffer) {
                audioChannel.trySend(buf)
            }
        }
        return generation
    }
    return -1
}
