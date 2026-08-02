package com.woka.ui.viewer.player

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import kotlin.math.max

/**
 * Seeks the VIDEO grabber to [targetMs]: keyframe seek with backoff, then decode
 * forward to the target keeping the last frame for immediate display. Video-only
 * — audio seeks separately via [audioSeekTo].
 */
internal fun VideoPlayerState.videoSeekTo(
    grabber: FFmpegFrameGrabber,
    targetMs: Long,
    videoChannel: Channel<VideoFrame>,
    hdrInfo: HdrInfo? = null
) {
    while (videoChannel.tryReceive().isSuccess) {}
    if (targetMs <= 0L) {
        try { grabber.restart() } catch (_: Exception) {}
        while (scope.isActive) {
            val frame = grabber.grabFrame(false, true, true, false, false) ?: break
            if (frame.image != null) { publishFrame(frame.toComposeBitmap(), hdrInfo); break }
        }
        return
    }
    var attempt = 0
    // Seeking exactly to the end lands past the last decodable frame, so start a
    // little earlier and decode forward.
    val seekBackoffMs = if (durationMs > 0 && targetMs >= durationMs - 1000L) 1000L else 0L
    var seekTime = max(0L, targetMs - seekBackoffMs) * 1000L
    while (attempt < 4) {
        try { grabber.setTimestamp(seekTime) } catch (_: Exception) {}
        if (grabber.timestamp <= targetMs * 1000L + 100000L) break // 100ms tolerance
        seekTime = max(0L, seekTime - 2000000L)
        attempt++
    }
    if (grabber.timestamp > targetMs * 1000L + 100000L) {
        try { grabber.restart() } catch (_: Exception) {}
    }
    var lastGoodFrame: Frame? = null
    while (scope.isActive) {
        val frame = grabber.grabFrame(false, true, true, false, false) ?: break
        if (grabber.timestamp >= targetMs * 1000L) {
            if (frame.image != null) { lastGoodFrame = frame.clone(); break }
        } else if (frame.image != null) {
            lastGoodFrame?.close()
            lastGoodFrame = frame.clone()
        }
    }
    lastGoodFrame?.let {
        if (it.image != null) publishFrame(it.toComposeBitmap(), hdrInfo)
        it.close()
    }
}

/** Seeks the AUDIO grabber to [targetMs] and clears buffered/queued audio. */
internal fun VideoPlayerState.audioSeekTo(
    grabber: FFmpegFrameGrabber,
    targetMs: Long,
    audioChannel: Channel<ByteArray>
) {
    while (audioChannel.tryReceive().isSuccess) {}
    soundLineInstance?.flush()
    if (targetMs <= 0L) {
        try { grabber.restart() } catch (_: Exception) {}
    } else {
        try { grabber.setTimestamp(targetMs * 1000L) } catch (_: Exception) {}
    }
}
