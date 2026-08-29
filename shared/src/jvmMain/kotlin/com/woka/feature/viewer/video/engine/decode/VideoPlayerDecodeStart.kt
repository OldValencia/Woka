package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.writeUiState
import com.woka.feature.viewer.video.engine.probe.probeChapters
import com.woka.feature.viewer.video.engine.probe.probeMediaTracks
import com.woka.feature.viewer.video.engine.subtitle.startSubtitleDecoderLoop
import com.woka.feature.viewer.video.engine.track.applyTrackPrefs
import com.woka.feature.viewer.video.engine.track.scanTrackSidecars

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
internal suspend fun VideoPlayerState.probeTracksAndChapters(grabber: FFmpegFrameGrabber) {
    try {
        // Read first, write second: the probes are IO and must not sit inside the
        // snapshot below, which is meant to be short.
        val probed = probeMediaTracks(grabber)
        // Guarded on its own, as the statement order used to guarantee: a container
        // whose chapters cannot be read must still get its tracks and its audio.
        val chapters = try {
            probeChapters(grabber, durationMs)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        // **Every snapshot-state write here goes inside one mutable snapshot**
        // (2026-08-29). This runs on `Dispatchers.IO`, and assigning Compose state
        // straight into the global snapshot from a background coroutine races the
        // recomposer advancing it: the write lands, reads back correctly on this
        // very thread, and is then gone — silently, with nothing logged and nothing
        // thrown. Measured: three audio tracks and `selectedAudio=2` at snapshot
        // 1239, every field back at its constructor value at 1248, with no other
        // writer of them in the module.
        //
        // The symptom was a file opening with **no audio track at all** — the probe
        // found three, the write was lost, `hasAudio` came out false, and the audio
        // loop returned before opening its grabber. Reopening the file usually fell
        // outside the window, which is what made it look intermittent; a cold start
        // (first launch after a build) widens it. See `code-style/state.md`.
        //
        // `hasAudio` is derived **inside** the block, not after it: it is a fact
        // about the list it is derived from, and applying the two separately lets
        // the same race split the pair.
        // The directory walk stays here, on the decoder's own dispatcher.
        val sidecars = scanTrackSidecars()
        writeUiState {
            // Restore the tracks and sync offsets this file was last watched
            // with, before anything downstream reads a selection (§2.6 / §3).
            applyTrackPrefs(probed, sidecars)
            tracks.chapters = chapters
            hasAudio = tracks.mediaTracks.audio.isNotEmpty()
        }
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
    // Completing the latch releases the audio loop, which reads `hasAudio` and the
    // selected index the block above applied.
    tracksProbed.complete(Unit)
}
