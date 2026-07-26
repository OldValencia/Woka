package io.iris.ui.viewer.player

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
    var hasAudio by mutableStateOf(false)
    var isRepeatMode by mutableStateOf(false)
    
    internal var soundLineInstance: SourceDataLine? = null
    internal var isPlayingBeforeSeek = false

    internal var grabberJob: Job? = null
    internal var presenterJob: Job? = null
    internal var audioJob: Job? = null
    internal var isSeeking = false
    
    @Volatile
    internal var seekTargetMs: Long = -1L

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
        // back on stale frames). The decoder still runs handleSeekIfNeeded and
        // decodes the last frame for display. The next Play then restarts from
        // the beginning (see togglePlayPause).
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
        presenterJob?.cancel()
        audioJob?.cancel()
    }

    private companion object {
        // How close to the end counts as "at the end".
        const val END_THRESHOLD_MS = 200L
    }
}
