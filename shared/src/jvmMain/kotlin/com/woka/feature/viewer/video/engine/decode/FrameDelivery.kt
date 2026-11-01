package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import com.woka.feature.viewer.video.engine.sync.mediaTimeUs
import com.woka.feature.viewer.video.engine.writeUiState

import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/**
 * The picture a decoded [frame] becomes: itself, or what the deinterlace graph
 * returns for it — null while `bwdif` waits for the next frame (VF-102).
 *
 * On the ordinary RGBA path every frame is looked at for the interlaced flag, and
 * the first one that carries it re-opens the grabber RAW behind the graph — before
 * the first paint, so the comb is never shown — unless the user turned it Off.
 */
internal suspend fun VideoPlayerState.pictureOf(grabber: FFmpegFrameGrabber, frame: Frame): Frame? {
    if (deinterlace.filtering) return deinterlace.process(frame)
    if (deinterlace.observe(frame)) {
        writeUiState { deinterlace.isInterlaced = true }
        if (deinterlace.enabled) requestDeinterlaceReopen(grabber.videoStream)
    }
    return frame
}

/** The seek path's [pictureOf]: it only lands frames, the decode loop observes them. */
internal fun VideoPlayerState.filteredPicture(frame: Frame): Frame? =
    if (deinterlace.filtering) deinterlace.process(frame) else frame

/** [picture]'s time on the media timeline, in µs: the graph's when filtering. */
internal fun VideoPlayerState.pictureTimeUs(grabber: FFmpegFrameGrabber, picture: Frame): Long =
    if (deinterlace.filtering) deinterlace.mediaTimeUs(picture) else grabber.mediaTimeUs

/**
 * Re-opens the video grabber in place, onto the same stream, so its frames take
 * the deinterlace path or leave it. The spinner only once something is on screen,
 * as for a rescale.
 */
internal fun VideoPlayerState.requestDeinterlaceReopen(fallbackVideoStream: Int) {
    isReloadingVideo = currentPositionMs > 0
    pendingVideoReopenIndex = tracks.selectedVideoIndex ?: fallbackVideoStream
}

/**
 * Paces, converts and sends one picture to the presenter. Moved out of the decode
 * loop when deinterlacing added a second source of pictures (VF-102).
 */
internal suspend fun VideoPlayerState.deliverPicture(
    grabber: FFmpegFrameGrabber,
    picture: Frame,
    pacer: FramePacer,
    videoChannel: Channel<VideoFrame>,
    frameGeneration: Int,
    hdrInfo: HdrInfo?
) {
    val frameTsMs = pictureTimeUs(grabber, picture) / 1000L
    // Already past its slot: decoded (the stream needs it) but not
    // converted, which is where the time actually goes (§6).
    if (pacer.shouldSkip(frameTsMs, playbackSpeed)) {
        generation.isBehind = pacer.isFallingBehind
        return
    }
    generation.isBehind = false
    val paced = pacer.convert(picture)
    videoChannel.send(
        VideoFrame(paced.bitmap, frameTsMs, frameGeneration, hdrInfo, paced.id)
    )
}
