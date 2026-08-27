package com.woka.feature.viewer.shell

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * Pointer gestures for the media area, assembled from three modifiers:
 * - [mediaZoomAndButtonPan] — `Ctrl + Scroll` zooms one geometric step, and
 *   `Ctrl` + a held button pans while the media overflows the panel.
 * - A plain drag pans too, with resistance past the edge and a clamp on release.
 * - [mediaTapGestures] — tap and double-tap.
 *
 * [panRequiresCtrl] drops the plain drag and lets `Ctrl + LMB` pan instead. Only
 * the PiP/fullscreen window sets it: there a plain left drag moves the window
 * (`pipWindowDraggable`), and until this gate existed it moved the picture with
 * it (TC-PIP-02).
 */
@Composable
fun Modifier.mediaViewerGestures(
    sizeMode: SizeMode,
    offsetX: Float,
    offsetY: Float,
    containerSize: IntSize,
    nativeSize: IntSize,
    videoState: VideoPlayerState?,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onPanActiveChange: (Boolean) -> Unit,
    onZoomHud: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleFullscreen: () -> Unit,
    panRequiresCtrl: Boolean = false
): Modifier {
    val curMode by rememberUpdatedState(sizeMode)
    val curOffsetX by rememberUpdatedState(offsetX)
    val curOffsetY by rememberUpdatedState(offsetY)
    val curContainer by rememberUpdatedState(containerSize)
    val curNative by rememberUpdatedState(nativeSize)

    fun applyZoom(percent: Int, anchor: Offset?) {
        val result = zoomTo(percent, anchor, curMode, curContainer, curNative, curOffsetX, curOffsetY)
        onOffsetChange(result.offsetX, result.offsetY)
        onSizeModeChange(result.sizeMode)
        onZoomHud()
    }

    fun pan(deltaX: Float, deltaY: Float) {
        val (x, y) = rubberBandOffset(
            curOffsetX + deltaX,
            curOffsetY + deltaY,
            curMode,
            curContainer,
            curNative
        )
        onOffsetChange(x, y)
    }

    fun settlePan() {
        onPanActiveChange(false)
        val (x, y) = clampOffset(curOffsetX, curOffsetY, curMode, curContainer, curNative)
        onOffsetChange(x, y)
    }

    val plainDragPan = if (panRequiresCtrl) {
        Modifier
    } else {
        Modifier.pointerInput(videoState) {
            detectDragGestures(
                onDragStart = { onPanActiveChange(true) },
                onDragEnd = { settlePan() },
                onDragCancel = { settlePan() }
            ) { change, drag ->
                // Unconditional, as of 2026-08-28: the picture is movable at every
                // zoom, and `panLimits` is the only thing that says how far.
                change.consume()
                pan(drag.x, drag.y)
            }
        }
    }

    return this
        .mediaZoomAndButtonPan(
            mode = { curMode },
            container = { curContainer },
            native = { curNative },
            ctrlPrimaryPan = panRequiresCtrl,
            onZoom = { percent, anchor -> applyZoom(percent, anchor) },
            onPan = { deltaX, deltaY -> pan(deltaX, deltaY) },
            onPanStart = { onPanActiveChange(true) },
            onPanEnd = { settlePan() }
        )
        .then(plainDragPan)
        .mediaTapGestures(
            videoState = videoState,
            mode = { curMode },
            container = { curContainer },
            native = { curNative },
            offset = { curOffsetX to curOffsetY },
            onSizeModeChange = onSizeModeChange,
            onOffsetChange = onOffsetChange,
            onZoomHud = onZoomHud,
            onPlayPause = onPlayPause,
            onToggleFullscreen = onToggleFullscreen
        )
}
