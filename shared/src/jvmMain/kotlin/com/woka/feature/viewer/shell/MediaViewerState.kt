package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ScalePreset

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
    // Panel and media geometry, published by MediaViewerContent. The size control
    // lives in the top bar and needs them to resolve a preset into a percentage.
    var containerSize by mutableStateOf(IntSize.Zero)
    var nativeSize by mutableStateOf(IntSize.Zero)
    // Bumped when the wheel or the keyboard changes the zoom, to flash the HUD.
    var zoomHudTrigger by mutableStateOf(0L)
    var activeItemPath by mutableStateOf<String?>(null)
    
    var playPauseTrigger by mutableStateOf(0L)
    var skipTrigger by mutableStateOf(0L)
    var skipDirection by mutableStateOf(1)
    
    var volumeTrigger by mutableStateOf(0L)
    var volumeTriggerTimer by mutableStateOf(0)
    // Single source of truth for the Volume/Subtitle hover popovers (see
    // PopoverHoverState) — replaces the four separate hover booleans that used to
    // reset each other and caused the popover flicker loop.
    val hover = PopoverHoverState()
    var isUiVisible by mutableStateOf(true)
    var lastInteractionTime by mutableStateOf(System.currentTimeMillis())
}

@Composable
fun rememberMediaViewerState(initialId: String): MediaViewerState {
    return remember(initialId) { MediaViewerState(initialId) }
}
