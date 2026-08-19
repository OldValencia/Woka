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
import com.woka.core.data.StillColorInfo

@Stable
class MediaViewerState(initialId: String) {
    var currentId by mutableStateOf(initialId)
    var animationDirection by mutableStateOf(0)
    var imageBitmap by mutableStateOf<ImageBitmap?>(null)
    // Which picture inside the current file is shown. Zero for the overwhelming
    // majority of files, which contain exactly one; reset on every file change,
    // because a burst's frame 4 means nothing in the photograph after it.
    var frameIndex by mutableStateOf(0)
    // Whether an animation is running. True by default: a GIF opens playing, which is
    // what every viewer that shows one does — and it is inert for a photograph, whose
    // frame set declares no durations and so has no clock (ME-140).
    var isFramePlaying by mutableStateOf(true)
    // How many pictures the current file holds, published by the frame strip so
    // PageUp/PageDown can stop at the ends without the key handler having to
    // know how a frame set is read.
    var frameCount by mutableStateOf(1)
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
    // The file's own dimensions, read from the decoder before it decodes. Flows
    // the other way to `nativeSize` above: this comes in from the loader so the
    // zoom presets mean native pixels even while a preview stands in for the
    // picture, that one is published outward once the media exists.
    var sourceSize by mutableStateOf(IntSize.Zero)
    // How the current still's pixels are encoded, so the frame can be drawn
    // through the right colour path. A video carries its own per-frame answer.
    var stillColor by mutableStateOf(StillColorInfo.Sdr)
    // Raised when the wheel or the keyboard changes the zoom, to flash the HUD.
    val zoomHudEvent = ViewerEvent()
    var activeItemPath by mutableStateOf<String?>(null)

    val playPauseEvent = ViewerEvent()
    val skipEvent = ViewerEvent()
    var skipDirection by mutableStateOf(1)

    // Also opens the volume popover for a moment; see rememberEventFlag.
    val volumeEvent = ViewerEvent()
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
