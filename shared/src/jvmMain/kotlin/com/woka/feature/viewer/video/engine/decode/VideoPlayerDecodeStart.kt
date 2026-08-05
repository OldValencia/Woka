package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.probe.logMediaTracks
import com.woka.feature.viewer.video.engine.probe.probeChapters
import com.woka.feature.viewer.video.engine.probe.probeMediaTracks
import com.woka.feature.viewer.video.engine.subtitle.startSubtitleDecoderLoop
import com.woka.feature.viewer.video.engine.track.applyTrackPrefs

import org.bytedeco.javacv.FFmpegFrameGrabber

/**
 * Everything that happens once, on a freshly started video grabber, before the
 * decode loop begins: what tracks the file has, which of them this file was last
 * watched with, its chapters, and the subtitle loop. Split out of
 * [startDecoderLoop] (§ file-size mandate) — that function is now only the loop.
 *
 * Failures here are caught and logged rather than propagated: a container whose
 * chapter or track metadata cannot be read still plays.
 */
internal fun VideoPlayerState.probeTracksAndChapters(grabber: FFmpegFrameGrabber) {
    try {
        // Restore the tracks and sync offsets this file was last watched
        // with, before anything downstream reads a selection (§2.6 / §3).
        applyTrackPrefs(probeMediaTracks(grabber))
        logMediaTracks(tracks.mediaTracks)
        // Same open format context, so this is free (§4). Reads durationMs,
        // which is why the caller assigns that first.
        tracks.chapters = probeChapters(grabber, durationMs)
        renderScale.useNativeOf(
            tracks.mediaTracks.video.firstOrNull { it.index == tracks.selectedVideoIndex }
        )
        // FFmpeg's own pick for the default video stream is not necessarily the one
        // flagged AV_DISPOSITION_DEFAULT, so on a multi-video container the picture
        // could come up on a different track than the menu reports as selected. Swap
        // onto the track we resolved (§2.3); the play head is still 0, so this costs
        // an open and no re-decode.
        tracks.selectedVideoIndex
            ?.takeIf { it != grabber.videoStream }
            ?.let { pendingVideoReopenIndex = it }
        startSubtitleDecoderLoop()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    // hasAudio comes from the authoritative full probe, not the audio grabber's
    // transient channel count. Completing the latch then releases the audio loop.
    hasAudio = tracks.mediaTracks.audio.isNotEmpty()
    tracksProbed.complete(Unit)
}
