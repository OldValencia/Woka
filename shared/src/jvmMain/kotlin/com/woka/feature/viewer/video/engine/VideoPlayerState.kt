package com.woka.feature.viewer.video.engine

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.PlaybackSpeed
import com.woka.core.data.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.io.File
import javax.sound.sampled.SourceDataLine
import com.woka.feature.viewer.video.model.Cue

@Stable
class VideoPlayerState(
    internal val scope: CoroutineScope,
    val file: File,
    initialVolume: Float
) {
    var isPlaying by mutableStateOf(true)
    var currentPositionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    var volume by mutableStateOf(initialVolume)
    var currentFrame by mutableStateOf<ImageBitmap?>(null)
    var currentFrameHdrInfo by mutableStateOf<HdrInfo?>(null)

    // The stream's own HDR metadata, written once per opened video track. Unlike
    // [currentFrameHdrInfo] — which is rewritten every frame — this is stable, so
    // the UI can read it without recomposing at the frame rate.
    var streamHdrInfo by mutableStateOf<HdrInfo?>(null)
        internal set

    var hasAudio by mutableStateOf(false)

    // Repeat (§6). Only File is a decoder concern — it re-seeks to 0 at EOF.
    // Folder is navigation, so the decoder treats it exactly like Off and lets
    // [reachedEnd] surface upwards; MediaViewerPlaylist decides what plays next.
    var repeatMode by mutableStateOf(RepeatMode.Off)

    // Raised by the presenter when playback runs off the end, cleared on any seek
    // or resume. The screen observes it to advance the folder playlist (§6).
    var reachedEnd by mutableStateOf(false)

    // Playback speed (§6), 0.5×–2×. The presenter divides its frame pacing by it
    // and the audio path runs an `atempo` filter so the pitch stays put.
    var playbackSpeed by mutableStateOf(PlaybackSpeed.NORMAL)

    // Bumped on every speed change so the audio loop rebuilds its filter graph.
    @Volatile
    internal var speedGeneration: Int = 0

    // True while the video grabber is being swapped onto another track (§2.3).
    // Re-opening a 4K HEVC stream and decoding back to the play head takes
    // seconds, so the UI shows a spinner over the last frame and the audio loop
    // parks — otherwise sound would run on while the picture is still catching up.
    var isReloadingVideo by mutableStateOf(false)
        internal set

    val tracks = VideoPlayerTracks()
    // Sidecar files count (Step 9): the toolbar icon and the SYNC row exist to
    // control whatever subtitles this file has, wherever they came from.
    val hasSubtitle: Boolean get() = tracks.hasAnySubtitle
    val activeCue: Cue? get() = tracks.activeCue
    internal val renderScale = RenderScale() // decode-size policy (§6)

    // Sync offsets (§3). Positive = that stream runs LATER than the video clock,
    // so what belongs at position P is the content originally at P − delay. Both
    // are applied at lookup time only — nothing ever re-decodes. See
    // VideoPlayerSync.kt for the helpers and the UI entry points.
    var subtitleDelayMs by mutableStateOf(0L)
    var audioDelayMs by mutableStateOf(0L)

    // Bumped whenever audioDelayMs changes. The audio loop re-seeks on it instead
    // of stalling on the lead throttle: shifting the target backwards would
    // otherwise just make the loop wait in silence for the clock to catch up.
    @Volatile
    internal var audioDelayGeneration: Int = 0
    
    internal var soundLineInstance: SourceDataLine? = null
    internal var isPlayingBeforeSeek = false

    internal var grabberJob: Job? = null       // video decode loop
    internal var audioDecodeJob: Job? = null   // audio decode loop (separate grabber)
    internal var presenterJob: Job? = null
    internal var audioJob: Job? = null          // sound-line writer
    internal var isSeeking = false

    // Authoritative seek target; both decode loops seek their grabber to it when
    // seekGeneration changes. Never -1 (the loops trigger on generation, not this).
    @Volatile
    internal var seekTargetMs: Long = 0L

    // Set by selectAudioTrack; the audio loop reopens its grabber on this absolute
    // stream index and clears it (§2.1). Video is untouched, so it never freezes.
    @Volatile
    internal var pendingAudioReopenIndex: Int? = null

    // Set by selectVideoTrack; the video loop swaps its own grabber onto this
    // absolute stream index and clears it (§2.3). Rare, so an in-place re-open is
    // accepted here rather than a permanently running second video decoder.
    @Volatile
    internal var pendingVideoReopenIndex: Int? = null

    // Set when the decode pipeline restarts underneath the presenter (§2.3). The
    // wall clock spent swapping is not playback time: measured against the old
    // baseline every incoming frame looks ~1s late, which is under the 1000ms
    // discontinuity threshold, so the presenter took the drop-to-catch-up path
    // and froze the picture for seconds while the decoder slowly made the gap up.
    @Volatile
    internal var rebasePresenterClock: Boolean = false

    // The generation the VIDEO loop has finished seeking to. The audio loop gates
    // playback on this so, after a seek, audio never starts before the frame has
    // actually landed on the target (no audio-before-video on slider scrubs).
    @Volatile
    internal var videoReadyGen: Int = 0

    // Set once the video grabber has started and enumerated tracks. The audio loop
    // waits for this before opening its own grabber, so the two grabbers never do
    // a concurrent first-open of the same file (which intermittently returned
    // incomplete stream info — no duration / no audio channels).
    @Volatile
    internal var tracksProbed: Boolean = false

    // Incremented on every seek. Frames carry the generation they were decoded
    // under; the presenter discards any frame whose generation is stale, so a
    // seek issued while playing can't be overwritten by buffered old frames.
    @Volatile
    internal var seekGeneration: Int = 0

    init {
        startPlayback()
    }

    fun updateVolume(newVolume: Float) {
        volume = newVolume
        soundLineInstance?.let { line ->
            setVolumeInternal(line, newVolume)
        }
    }

    // Transport (seek / play-pause) lives in VideoPlayerControls.kt.

    fun dispose() {
        grabberJob?.cancel()
        audioDecodeJob?.cancel()
        presenterJob?.cancel()
        audioJob?.cancel()
    }
}
