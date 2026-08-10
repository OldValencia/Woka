package com.woka.feature.viewer.video.engine

import com.woka.feature.viewer.video.engine.decode.FrameRetention
import com.woka.feature.viewer.video.engine.decode.RenderScale
import com.woka.feature.viewer.video.engine.decode.setVolumeInternal
import com.woka.feature.viewer.video.engine.decode.startPlayback
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import com.woka.feature.viewer.video.engine.track.VideoPlayerTracks

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.PlaybackSpeed
import com.woka.core.data.RepeatMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.io.File
import javax.sound.sampled.SourceDataLine

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
    // Where unmuting comes back to (see `toggleMute`). Nothing draws it, so a plain var.
    var volumeBeforeMute = initialVolume.takeIf { it > 0f } ?: DEFAULT_UNMUTE_VOLUME
    var currentFrame by mutableStateOf<ImageBitmap?>(null)
    var currentFrameHdrInfo by mutableStateOf<HdrInfo?>(null)

    // Identifies [currentFrame] for [frameRetention]. Published with the bitmap, so
    // a composition that reads both gets a matched pair; a seek frame leaves it on
    // the previous value, which only ever makes the retention rule more cautious.
    var currentFrameId by mutableStateOf(0L)
        internal set

    /**
     * Frees a frame's pixels once every scene has drawn past it (§6). Draw sites
     * must call `acknowledge(currentFrameId)` from their draw scope — a site that
     * does not simply holds memory back, it can never free a frame early.
     */
    internal val frameRetention = FrameRetention()

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

    // True while the video grabber is being swapped onto another track (§2.3).
    // Re-opening a 4K HEVC stream and decoding back to the play head takes
    // seconds, so the UI shows a spinner over the last frame and the audio loop
    // parks — otherwise sound would run on while the picture is still catching up.
    var isReloadingVideo by mutableStateOf(false)
        internal set

    val tracks = VideoPlayerTracks()
    internal val renderScale = RenderScale() // decode-size policy (§6)

    // Displayed frame rate against the rate the file declares. Read by the notice
    // that explains a struggling machine; costs one call per presented frame.
    internal val health = PlaybackHealth()

    // Sync offsets (§3). Positive = that stream runs LATER than the video clock,
    // so what belongs at position P is the content originally at P − delay. Both
    // are applied at lookup time only — nothing ever re-decodes. See
    // VideoPlayerSync.kt for the helpers and the UI entry points.
    var subtitleDelayMs by mutableStateOf(0L)
    var audioDelayMs by mutableStateOf(0L)

    // The seek / speed / audio-offset handshake between the three playback
    // coroutines. Was six loose @Volatile fields here, with the rules only in
    // comments; see PlaybackGeneration.
    internal val generation = PlaybackGeneration()

    internal var soundLineInstance: SourceDataLine? = null
    internal var isPlayingBeforeSeek = false

    internal var grabberJob: Job? = null       // video decode loop
    internal var audioDecodeJob: Job? = null   // audio decode loop (separate grabber)
    internal var presenterJob: Job? = null
    internal var audioJob: Job? = null          // sound-line writer

    // Volatile: written on the UI thread by pauseForSeek/resumeAfterSeek and read
    // from all three decode coroutines. It was a plain Boolean, which the JMM lets
    // a loop hoist out and never re-read.
    @Volatile
    internal var isSeeking = false

    // Set by selectAudioTrack; the audio loop reopens its grabber on this absolute
    // stream index and clears it (§2.1). Video is untouched, so it never freezes.
    @Volatile
    internal var pendingAudioReopenIndex: Int? = null

    // Set by selectVideoTrack; the video loop swaps its own grabber onto this
    // absolute stream index and clears it (§2.3). Rare, so an in-place re-open is
    // accepted here rather than a permanently running second video decoder.
    @Volatile
    internal var pendingVideoReopenIndex: Int? = null

    // Completed once the video grabber has started and enumerated tracks. The audio
    // loop awaits it before opening its own grabber, so the two never do a
    // concurrent first-open of the same file (which intermittently returned
    // incomplete stream info — no duration / no audio channels). A one-shot latch
    // is what this always was; it used to be a Boolean polled every 10ms.
    internal val tracksProbed = CompletableDeferred<Unit>()

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
