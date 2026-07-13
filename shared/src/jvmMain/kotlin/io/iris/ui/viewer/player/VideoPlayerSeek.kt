package io.iris.ui.viewer.player

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import javax.sound.sampled.SourceDataLine

internal fun VideoPlayerState.handleSeekIfNeeded(
    grabber: FFmpegFrameGrabber,
    converter: Java2DFrameConverter,
    videoChannel: Channel<Pair<ImageBitmap, Long>>,
    audioChannel: Channel<ByteArray>,
    soundLine: SourceDataLine?
) {
    if (seekTargetMs != -1L) {
        val target = seekTargetMs
        seekTargetMs = -1L
        
        if (target == 0L) {
            try { grabber.restart() } catch (_: Exception) {}
            
            while (videoChannel.tryReceive().isSuccess) {}
            while (audioChannel.tryReceive().isSuccess) {}
            soundLine?.flush()
            
            while (scope.isActive) {
                val frame = grabber.grabFrame() ?: break
                if (frame.image != null) {
                    val bufferedImage = converter.getBufferedImage(frame)
                    currentFrame = bufferedImage.toComposeBitmap()
                    break
                }
            }
        } else {
            var attempt = 0
            var seekTime = target * 1000L
            while (attempt < 4) {
                try { grabber.setTimestamp(seekTime) } catch (_: Exception) {}
                if (grabber.timestamp <= target * 1000L + 100000L) { // Allow 100ms tolerance
                    break
                } else {
                    seekTime = kotlin.math.max(0L, seekTime - 2000000L)
                    attempt++
                }
            }
            if (grabber.timestamp > target * 1000L + 100000L) {
                try { grabber.restart() } catch (_: Exception) {}
            }
            
            while (videoChannel.tryReceive().isSuccess) {}
            while (audioChannel.tryReceive().isSuccess) {}
            soundLine?.flush()
            
            var lastGoodFrame: org.bytedeco.javacv.Frame? = null
            while (scope.isActive) {
                val frame = grabber.grabFrame() ?: break
                if (frame.image != null) {
                    lastGoodFrame = frame.clone()
                    if (grabber.timestamp >= target * 1000L) {
                        break
                    }
                }
            }
            if (lastGoodFrame != null && lastGoodFrame.image != null) {
                val bufferedImage = converter.getBufferedImage(lastGoodFrame)
                currentFrame = bufferedImage.toComposeBitmap()
                lastGoodFrame.close()
            }
        }
    }
}
