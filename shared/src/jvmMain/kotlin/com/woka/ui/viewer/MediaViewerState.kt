package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.data.ScalePreset

@Stable
class MediaViewerState(initialId: String) {
    var currentId by mutableStateOf(initialId)
    var animationDirection by mutableStateOf(0)
    var imageBitmap by mutableStateOf<ImageBitmap?>(null)
    // Semantic size state (drives the indicator, clicks, Ctrl+Scroll).
    var sizeMode by mutableStateOf<SizeMode>(SizeMode.Preset(ScalePreset.Fit))
    // Derived graphics-layer scale published by MediaViewerContent, consumed by the
    // blurred carousel background in MediaViewerToolbar. Do not mutate directly.
    var scale by mutableStateOf(1f)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    // Bumped whenever the user attempts Ctrl+Scroll zoom while in Preset Mode.
    var scalingDisabledTrigger by mutableStateOf(0L)
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
