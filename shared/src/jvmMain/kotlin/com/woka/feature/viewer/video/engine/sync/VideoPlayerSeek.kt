package com.woka.feature.viewer.video.engine.sync

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.decode.VideoFrame
import com.woka.feature.viewer.video.engine.decode.publishFrame
import com.woka.feature.viewer.video.engine.probe.HdrInfo

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import kotlin.math.max
import com.woka.feature.viewer.video.engine.decode.toComposeBitmap

/**
 * Seeks the VIDEO grabber to [targetMs]: keyframe seek with backoff, then decode
 * forward to the target keeping the last frame for immediate display. Video-only
 * — audio seeks separately via [audioSeekTo]. Times are on the media timeline
 * ([mediaTimeUs]).
 *
 * When nothing lands — a container that cannot seek leaves the grabber at EOF
 * (VF-001: `.thp`, `.rpl`, `.mtv`, `.nsv`) — the file is restarted and decoded
 * forward from the start, as [grabImageAt] does for previews.
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
        if (grabber.mediaTimeUs <= targetMs * 1000L + 100000L) break // 100ms tolerance
        seekTime = max(0L, seekTime - 2000000L)
        attempt++
    }
    if (grabber.mediaTimeUs > targetMs * 1000L + 100000L) {
        try { grabber.restart() } catch (_: Exception) {}
    }
    val landed = decodeForwardTo(grabber, targetMs) ?: run {
        try { grabber.restart() } catch (_: Exception) {}
        decodeForwardTo(grabber, targetMs)
    }
    landed?.let {
        if (it.image != null) publishFrame(it.toComposeBitmap(), hdrInfo)
        it.close()
    }
}

/** The first picture at [targetMs], or the last one before the stream ended. */
private fun VideoPlayerState.decodeForwardTo(grabber: FFmpegFrameGrabber, targetMs: Long): Frame? {
    var lastGoodFrame: Frame? = null
    while (scope.isActive) {
        val frame = grabber.grabFrame(false, true, true, false, false) ?: break
        if (grabber.mediaTimeUs >= targetMs * 1000L) {
            if (frame.image != null) { lastGoodFrame?.close(); lastGoodFrame = frame.clone(); break }
        } else if (frame.image != null) {
            lastGoodFrame?.close()
            lastGoodFrame = frame.clone()
        }
    }
    return lastGoodFrame
}

/**
 * Seeks the AUDIO grabber to [targetMs] and clears buffered/queued audio. A
 * container that cannot seek is restarted, and audio that stopped short — a long
 * `.amv` returns 500 frames from the start, 40 s for a 450 s target (VF-001,
 * 2026-11-01) — is decoded forward to the target. Cheap next to video, and
 * without it the loop's lag check would re-seek forever.
 */
internal fun VideoPlayerState.audioSeekTo(
    grabber: FFmpegFrameGrabber,
    targetMs: Long,
    audioChannel: Channel<ByteArray>
) {
    while (audioChannel.tryReceive().isSuccess) {}
    soundLineInstance?.flush()
    if (targetMs <= 0L) {
        try { grabber.restart() } catch (_: Exception) {}
        return
    }
    val seeked = runCatching { grabber.setTimestamp(targetMs * 1000L) }.isSuccess
    if (!seeked) try { grabber.restart() } catch (_: Exception) {}
    while (scope.isActive && grabber.mediaTimeUs < targetMs * 1000L - 100000L) { // 100ms tolerance
        grabber.grabSamples() ?: break
    }
}
