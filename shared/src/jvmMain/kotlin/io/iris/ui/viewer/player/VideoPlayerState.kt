package io.iris.ui.viewer.player

import androidx.compose.runtime.*
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
        seekTargetMs = safeProgress
        currentPositionMs = safeProgress
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
        if (!isPlaying && durationMs > 0 && currentPositionMs >= durationMs - 200) {
            seek(0)
        }
        isPlaying = !isPlaying
    }

    fun dispose() {
        grabberJob?.cancel()
        presenterJob?.cancel()
        audioJob?.cancel()
    }
}

@Composable
fun rememberVideoPlayerState(file: File, initialVolume: Float): VideoPlayerState {
    val scope = rememberCoroutineScope()
    val state = remember(file) { VideoPlayerState(scope, file, initialVolume) }
    
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }
    
    return state
}
