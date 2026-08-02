package com.woka.ui.viewer.player

import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber

/** HDR metadata of the video stream this grabber is currently decoding. */
internal fun FFmpegFrameGrabber.readStreamHdrInfo(): HdrInfo {
    val codecpar = formatContext.streams(videoStream).codecpar()
    return readHdrInfo(codecpar.color_trc(), codecpar.color_primaries())
}

/**
 * Swaps the video grabber onto [videoStreamIndex] and decodes back to the current
 * play head (§2.3).
 *
 * Unlike the audio path (§2.1), this tears down and re-opens the video decode
 * context in place: containers with more than one video stream are rare, so a
 * brief re-decode on switch is a better trade than keeping a second video decoder
 * alive for a case almost no file hits. Audio is untouched and keeps running off
 * its own grabber, and the play head does not move, so the two stay in sync.
 */
internal fun VideoPlayerState.reopenVideoGrabber(
    old: FFmpegFrameGrabber,
    videoStreamIndex: Int,
    videoChannel: Channel<VideoFrame>
): FFmpegFrameGrabber {
    while (videoChannel.tryReceive().isSuccess) {}
    try { old.stop(); old.release() } catch (_: Exception) {}

    val grabber = openVideoGrabber(videoStreamIndex)
    grabber.start()
    // Decode back to where the user was, tagged with the NEW track's HDR metadata
    // so the landed frame is drawn through the right painter from the first paint.
    val resumeMs = currentPositionMs
    if (resumeMs > 0) videoSeekTo(grabber, resumeMs, videoChannel, grabber.readStreamHdrInfo())
    return grabber
}

/** Selects a video track by absolute stream index and requests a video re-open. */
fun VideoPlayerState.selectVideoTrack(index: Int) {
    if (tracks.selectedVideoIndex == index) return
    tracks.selectedVideoIndex = index
    isReloadingVideo = true
    pendingVideoReopenIndex = index
}
