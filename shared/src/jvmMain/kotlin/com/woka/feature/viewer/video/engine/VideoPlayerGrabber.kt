package com.woka.feature.viewer.video.engine

import kotlinx.coroutines.channels.Channel
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameGrabber
import com.woka.feature.viewer.video.model.MediaTrack

/**
 * Opens a video-only grabber (audio is decoded on its own grabber, §2.1) on
 * [videoStreamIndex] — the absolute container index, matching [MediaTrack.index];
 * -1 leaves the container default in place (used for the first open, before the
 * probe has run).
 */
internal fun VideoPlayerState.openVideoGrabber(videoStreamIndex: Int = -1): FFmpegFrameGrabber {
    val grabber = FFmpegFrameGrabber(file)
    grabber.setVideoOption("threads", "auto") // SW decode: dodge D3D11VA half-frame scaling bugs
    grabber.pixelFormat = AV_PIX_FMT_RGBA
    if (videoStreamIndex >= 0) grabber.videoStream = videoStreamIndex
    // Output at the size actually drawn (§6). No-op until the probe has filled in
    // the native dimensions, so the very first open is always native.
    renderScale.applyTo(grabber)
    return grabber
}

/**
 * Opens an audio-only grabber for [audioStreamIndex] (absolute container index;
 * -1 = default). JavaCV matches audioStream against the absolute index, so this
 * is exactly [MediaTrack.index].
 */
internal fun VideoPlayerState.openAudioGrabber(audioStreamIndex: Int): FFmpegFrameGrabber {
    val grabber = FFmpegFrameGrabber(file)
    grabber.audioChannels = 2 // Downmix 5.1/7.1 to stereo to avoid Java AudioSystem crashes
    grabber.sampleFormat = AV_SAMPLE_FMT_S16
    // Disable video on this grabber: no stream matches Int.MAX_VALUE, so video_st
    // stays null. Otherwise setTimestamp would decode 4K HEVC forward to reach the
    // exact position — the real multi-second cost of an audio switch/seek (§2.1).
    grabber.videoStream = Int.MAX_VALUE
    // Streams are already known from the video grabber, so probe less on open.
    grabber.setOption("probesize", "2000000")       // 2 MB
    grabber.setOption("analyzeduration", "1000000")  // 1 s
    if (audioStreamIndex >= 0) grabber.audioStream = audioStreamIndex
    return grabber
}

/**
 * Re-opens the audio grabber on a new track (§2.1). Only the audio path is torn
 * down — the video pipeline keeps running, so switching never freezes the frame.
 */
internal fun VideoPlayerState.reopenAudioGrabber(
    old: FFmpegFrameGrabber,
    audioStreamIndex: Int,
    audioChannel: Channel<ByteArray>
): FFmpegFrameGrabber {
    while (audioChannel.tryReceive().isSuccess) {}
    disposeAudioLine()
    try { old.stop(); old.release() } catch (_: Exception) {}

    val grabber = openAudioGrabber(audioStreamIndex)
    grabber.start()
    hasAudio = grabber.audioChannels > 0
    if (hasAudio) initAudio(grabber, audioChannel)
    // Read the live position AFTER the (latency-carrying) open so the new track
    // lines up with the video clock; the loop's lag-resync mops up any residual.
    val resumeMs = audioTimeFor(currentPositionMs)
    if (resumeMs > 0) try { grabber.setTimestamp(resumeMs * 1000L) } catch (_: Exception) {}
    return grabber
}

internal fun VideoPlayerState.disposeAudioLine() {
    audioJob?.cancel()
    audioJob = null
    soundLineInstance?.let {
        try { it.stop(); it.flush(); it.close() } catch (_: Exception) {}
    }
    soundLineInstance = null
}

/** Selects an audio track by absolute stream index and requests an audio re-open. */
fun VideoPlayerState.selectAudioTrack(index: Int) {
    if (tracks.selectedAudioIndex == index) return
    tracks.selectedAudioIndex = index
    pendingAudioReopenIndex = index
    tracks.markChosen(TrackChoice.Audio)
    persistTrackPrefs()
}
