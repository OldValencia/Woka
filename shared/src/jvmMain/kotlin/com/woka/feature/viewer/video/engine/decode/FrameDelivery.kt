package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import com.woka.feature.viewer.video.engine.probe.readFrom
import com.woka.feature.viewer.video.engine.probe.withSceneOf
import com.woka.feature.viewer.video.engine.sync.mediaTimeUs
import com.woka.feature.viewer.video.engine.writeUiState

import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/**
 * The picture a decoded [frame] becomes: itself, or what the picture graph
 * returns for it — null while `bwdif` waits for the next frame (VF-102, VF-105).
 *
 * On the ordinary RGBA path every frame is looked at for the interlaced flag, and
 * the first one that carries it re-opens the grabber RAW behind the graph — before
 * the first paint, so the comb is never shown — unless the user turned it Off. That
 * frame itself is held back.
 */
internal suspend fun VideoPlayerState.pictureOf(grabber: FFmpegFrameGrabber, frame: Frame): Frame? {
    // Looked at on both paths: a rotated file already runs RAW behind the graph,
    // and its graph is rebuilt with `bwdif` once a flagged frame turns up.
    if (pictureFilter.observe(frame)) {
        writeUiState { pictureFilter.isInterlaced = true }
        if (pictureFilter.deinterlaceEnabled) {
            requestPictureReopen(grabber.videoStream)
            // Not shown: shown, it moves the play head, and the re-open then seeks
            // back to it instead of starting over — on a cut 1080i H.264 `.ts` that
            // seek landed 1.3 s late and the run of late pictures was dropped
            // (measured 2026-11-01, `TruncatedFileProbe`).
            return null
        }
    }
    return if (pictureFilter.filtering) pictureFilter.process(frame) else frame
}

/** The seek path's [pictureOf]: it only lands frames, the decode loop observes them. */
internal fun VideoPlayerState.filteredPicture(frame: Frame): Frame? =
    if (pictureFilter.filtering) pictureFilter.process(frame) else frame

/** [picture]'s time on the media timeline, in µs: the graph's when filtering. */
internal fun VideoPlayerState.pictureTimeUs(grabber: FFmpegFrameGrabber, picture: Frame): Long =
    if (pictureFilter.filtering) pictureFilter.mediaTimeUs(picture) else grabber.mediaTimeUs

/**
 * Re-opens the video grabber in place, onto the same stream, so its frames take
 * the picture graph or leave it. The spinner only once something is on screen,
 * as for a rescale.
 */
internal fun VideoPlayerState.requestPictureReopen(fallbackVideoStream: Int) {
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
        VideoFrame(paced.bitmap, frameTsMs, frameGeneration, colourOf(hdrInfo, grabber), paced.id)
    )
}

/**
 * The colour of the picture [grabber] last returned: the film's declared white point
 * (ME-301) and its scene's HDR10+ peak (ME-305), read off that picture's own side data
 * (ME-237). The player, a lane and a seek all take it from the one place that decoded the frame.
 *
 * **The white point is read once**: once the stream has it, [hdr] is replaced by the stream's
 * own, so a later frame without the side data cannot fall to the assumed value and flicker.
 * Only the scene's peak is read per picture.
 */
internal fun VideoPlayerState.colourOf(hdr: HdrInfo?, grabber: FFmpegFrameGrabber): HdrInfo? {
    val known = streamHdrInfo?.takeIf {
        it.whitePoint != null && it.transfer == hdr?.transfer && it.isWideGamut == hdr.isWideGamut
    }
    val picture = grabber.lastPicture
    return (known ?: hdr)?.readFrom(picture)?.withSceneOf(picture)
}
