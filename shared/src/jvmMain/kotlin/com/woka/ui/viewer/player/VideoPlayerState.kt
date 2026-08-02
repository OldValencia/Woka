package com.woka.ui.viewer.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
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
    var currentFrame by mutableStateOf<ImageBitmap?>(null)
    var currentFrameHdrInfo by mutableStateOf<HdrInfo?>(null)
    var hasAudio by mutableStateOf(false)
    var isRepeatMode by mutableStateOf(false)

    // True while the video grabber is being swapped onto another track (§2.3).
    // Re-opening a 4K HEVC stream and decoding back to the play head takes
    // seconds, so the UI shows a spinner over the last frame and the audio loop
    // parks — otherwise sound would run on while the picture is still catching up.
    var isReloadingVideo by mutableStateOf(false)
        internal set

    val tracks = VideoPlayerTracks()
    val hasSubtitle: Boolean get() = tracks.hasSubtitle
    val activeCue: Cue? get() = tracks.activeCue
    
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

    fun seek(progressMs: Long) {
        val safeProgress = progressMs.coerceIn(0L, durationMs)
        seekGeneration++
        seekTargetMs = safeProgress
        currentPositionMs = safeProgress

        // Seeking to (or past) the very end is treated as reaching the end:
        // pin the play head there and pause, instead of relying on the decoder
        // to play through to EOF (which is racy and lets the position bounce
        // back on stale frames). The decoder still seeks and decodes the last
        // frame for display. The next Play then restarts from the beginning
        // (see togglePlayPause).
        if (durationMs > 0 && safeProgress >= durationMs - END_THRESHOLD_MS) {
            currentPositionMs = durationMs
            isPlayingBeforeSeek = false
            isPlaying = false
        }
    }
    
    fun pauseForSeek() {
        if (!isSeeking) {
            isPlayingBeforeSeek = isPlaying
            isSeeking = true
            isPlaying = false
        }
    }
    
    fun resumeAfterSeek() {
        if (isSeeking) {
            isSeeking = false
            if (isPlayingBeforeSeek) {
                isPlaying = true
            }
        }
    }

    fun togglePlayPause() {
        if (!isPlaying && durationMs > 0 && currentPositionMs >= durationMs - END_THRESHOLD_MS) {
            seek(0)
        }
        isPlaying = !isPlaying
    }

    fun dispose() {
        grabberJob?.cancel()
        audioDecodeJob?.cancel()
        presenterJob?.cancel()
        audioJob?.cancel()
    }

    private companion object {
        // How close to the end counts as "at the end".
        const val END_THRESHOLD_MS = 200L
    }
}
