package com.woka.feature.viewer.video.engine.sync

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.decode.VideoFrame
import com.woka.feature.viewer.video.engine.decode.PictureFilter
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
    landAt(grabber, pictureFilter, targetMs)?.let {
        if (it.image != null) publishFrame(it.toComposeBitmap(), hdrInfo)
        it.close()
    }
}

/**
 * Puts [grabber] on [targetMs] and returns the picture there — a clone the caller closes —
 * with the grabber left just after it. The player's seek and a decoder lane's pre-roll
 * (ME-232) are the same landing: keyframe seek with backoff, then decode forward, through
 * whichever [filter] belongs to that grabber. [alive] stops a lane that was let go.
 */
internal fun VideoPlayerState.landAt(
    grabber: FFmpegFrameGrabber,
    filter: PictureFilter,
    targetMs: Long,
    alive: () -> Boolean = { scope.isActive }
): Frame? {
    // The graph holds neighbouring frames from before the jump (VF-102).
    filter.reset()
    if (targetMs <= 0L) {
        try { grabber.restart() } catch (_: Exception) {}
        while (alive()) {
            val frame = grabber.grabFrame(false, true, true, false, false) ?: break
            if (frame.image == null) continue
            val picture = filter.picture(frame) ?: continue
            return picture.clone()
        }
        return null
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
    filter.reset()
    return decodeForwardTo(grabber, filter, targetMs, alive) ?: run {
        try { grabber.restart() } catch (_: Exception) {}
        filter.reset()
        decodeForwardTo(grabber, filter, targetMs, alive)
    }
}

/**
 * The first picture at [targetMs], or the last one before the stream ended. When
 * deinterlacing, a picture is the graph's — one frame behind the grabber — and its
 * own time is what is compared.
 */
private fun decodeForwardTo(grabber: FFmpegFrameGrabber, filter: PictureFilter, targetMs: Long, alive: () -> Boolean): Frame? {
    var lastGoodFrame: Frame? = null
    while (alive()) {
        val frame = grabber.grabFrame(false, true, true, false, false) ?: break
        if (frame.image == null) continue
        val picture = filter.picture(frame) ?: continue
        lastGoodFrame?.close()
        lastGoodFrame = picture.clone()
        if (filter.timeUs(grabber, picture) >= targetMs * 1000L) break
    }
    return lastGoodFrame
}

/** [frame] through [this] graph, or itself on the plain path — `filteredPicture` for any filter. */
private fun PictureFilter.picture(frame: Frame): Frame? = if (filtering) process(frame) else frame

/** A picture's media time through [this] filter — `pictureTimeUs` for any filter. */
internal fun PictureFilter.timeUs(grabber: FFmpegFrameGrabber, picture: Frame): Long =
    if (filtering) mediaTimeUs(picture) else grabber.mediaTimeUs

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
