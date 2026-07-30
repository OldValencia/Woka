package com.woka.ui.viewer.pip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.AppDependencies
import com.woka.data.MediaItem
import com.woka.data.ScalePreset
import com.woka.navigation.Screen
import com.woka.ui.viewer.SizeMode
import com.woka.ui.viewer.startSizeMode
import com.woka.ui.viewer.player.VideoPlayerState
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

object PipManager {
    val pipScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    var isPipActive by mutableStateOf(false)
        private set
        
    var activeItem: MediaItem? by mutableStateOf(null)
        private set
        
    var videoState: VideoPlayerState? by mutableStateOf(null)
        private set

    var sizeMode: SizeMode by mutableStateOf(SizeMode.Preset(ScalePreset.Fit))
    // Derived graphics-layer scale published by MediaViewerContent.
    var scale by mutableStateOf(1f)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)

    var isTransitioningToMain = false

    fun createOrGetState(item: MediaItem, initialVolume: Float): VideoPlayerState {
        if (activeItem?.file?.absolutePath == item.file.absolutePath && videoState != null) {
            return videoState!!
        }
        return VideoPlayerState(pipScope, item.file, initialVolume)
    }

    fun openPip(
        item: MediaItem,
        state: VideoPlayerState,
        initialSizeMode: SizeMode = startSizeMode(AppDependencies.settingsRepository.settings.value),
        initialOffsetX: Float = 0f,
        initialOffsetY: Float = 0f
    ) {
        if (isPipActive && videoState != state) {
            videoState?.dispose()
        }
        activeItem = item
        videoState = state
        isPipActive = true
        // Preserve the current size state across the fullscreen transition.
        sizeMode = initialSizeMode
        scale = 1f
        offsetX = initialOffsetX
        offsetY = initialOffsetY
    }

    fun closePip(returnToMain: Boolean = false) {
        if (returnToMain && activeItem != null) {
            isTransitioningToMain = true
            AppDependencies.router.replaceTop(
                Screen.Folder(
                    path = activeItem!!.file.parent,
                    openViewerForId = activeItem!!.file.absolutePath,
                    nonce = System.currentTimeMillis()
                )
            )
            pipScope.launch {
                delay(500.milliseconds)
                isTransitioningToMain = false
            }
        } else {
            videoState?.dispose()
            videoState = null
            activeItem = null
        }
        isPipActive = false
    }

    fun releaseState(state: VideoPlayerState?) {
        if (state == null) return
        if (videoState == state && isPipActive) {
            // Keep it alive, PIP is using it!
        } else if (videoState == state && isTransitioningToMain) {
            // Keep it alive, we are transitioning to Main!
        } else {
            state.dispose()
            if (videoState == state) {
                videoState = null
                activeItem = null
            }
        }
    }
}
