package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

@Stable
class MediaViewerState(initialId: String) {
    var currentId by mutableStateOf(initialId)
    var animationDirection by mutableStateOf(0)
    var imageBitmap by mutableStateOf<ImageBitmap?>(null)
    var scale by mutableStateOf(1f)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    var activeItemPath by mutableStateOf<String?>(null)
    
    var playPauseTrigger by mutableStateOf(0L)
    var skipTrigger by mutableStateOf(0L)
    var skipDirection by mutableStateOf(1)
    
    var volumeTrigger by mutableStateOf(0L)
    var isVolumeHoveredRaw by mutableStateOf(false)
    var isVolumeHovered by mutableStateOf(false)
    var volumeTriggerTimer by mutableStateOf(0)
    var isUiVisible by mutableStateOf(true)
    var lastInteractionTime by mutableStateOf(System.currentTimeMillis())
}

@Composable
fun rememberMediaViewerState(initialId: String): MediaViewerState {
    return remember(initialId) { MediaViewerState(initialId) }
}
