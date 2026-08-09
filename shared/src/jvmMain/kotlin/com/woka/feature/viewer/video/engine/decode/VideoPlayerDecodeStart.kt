package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.VideoPlayerState
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
        // TEMPORARY (§audio-icon-regression) — the crossed-out speaker means
        // `hasAudio` came out false, and every route to that runs through this one
        // try block, so reading the log tells us which:
        //   "probe fc=null"          -> the format context was gone; start() half-failed
        //   "probe streams=N audio=0" -> the container really was walked and found none
        //   "probe threw"            -> applyTrackPrefs/scanExternalSubtitles blew up and
        //                               mediaTracks was left EMPTY
        // Absence of all three with a crossed icon means hasAudio was cleared later,
        // which only reopenAudioGrabber does (VideoPlayerGrabber.kt:67).
        println("WOKA-AUDIO: probe fc=${grabber.formatContext == null} streams=${grabber.formatContext?.nb_streams()}")
        // Restore the tracks and sync offsets this file was last watched
        // with, before anything downstream reads a selection (§2.6 / §3).
        applyTrackPrefs(probeMediaTracks(grabber))
        println("WOKA-AUDIO: probe walked audio=${tracks.mediaTracks.audio.size} selected=${tracks.selectedAudioIndex}")
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
        println("WOKA-AUDIO: probe threw ${e::class.simpleName}: ${e.message}") // TEMPORARY (§audio-icon-regression)
        e.printStackTrace()
    }
    // hasAudio comes from the authoritative full probe, not the audio grabber's
    // transient channel count. Completing the latch then releases the audio loop.
    hasAudio = tracks.mediaTracks.audio.isNotEmpty()
    println("WOKA-AUDIO: probe done hasAudio=$hasAudio") // TEMPORARY (§audio-icon-regression)
    tracksProbed.complete(Unit)
}
