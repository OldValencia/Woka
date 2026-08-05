package com.woka.feature.viewer.shell

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pointer gestures for the media area:
 * - `Ctrl + Scroll` zooms one geometric step — **always**, there is no state in
 *   which the gesture is refused — keeping the point under the cursor in place.
 * - Dragging pans whenever the media overflows the panel (`Ctrl` + right button
 *   still works), with resistance past the edge and a clamp on release.
 * - Taps are handled by [mediaTapGestures].
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
    onToggleFullscreen: () -> Unit
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

    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                var panning = false
                var lastPos = Offset.Zero
                while (true) {
                    // Initial pass so Ctrl+RMB pan is intercepted before the child
                    // ContextMenuArea; non-Ctrl events are left unconsumed and still
                    // reach the context menu / tap detector on the Main pass.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.first()
                    val isCtrl = event.keyboardModifiers.isCtrlPressed ||
                        event.keyboardModifiers.isMetaPressed

                    if (event.type == PointerEventType.Scroll && isCtrl) {
                        val delta = change.scrollDelta.y
                        val bounds = zoomBounds(curContainer, curNative)
                        var percent = curMode.percentIn(curContainer, curNative)
                        // Trackpads report several units in one event; repeat the
                        // geometric step rather than scaling a flat one.
                        repeat(abs(delta).roundToInt().coerceIn(1, 3)) {
                            percent = if (delta > 0) {
                                stepDown(percent, bounds)
                            } else {
                                stepUp(percent, bounds)
                            }
                        }
                        applyZoom(percent, change.position)
                        event.changes.forEach { it.consume() }
                        continue
                    }

                    val canPan = contentOverflows(curMode, curContainer, curNative)
                    if (isCtrl && event.buttons.isSecondaryPressed && canPan) {
                        if (!panning) {
                            panning = true
                            lastPos = change.position
                            onPanActiveChange(true)
                        } else {
                            val moved = change.position - lastPos
                            lastPos = change.position
                            pan(moved.x, moved.y)
                        }
                        change.consume()
                    } else if (panning) {
                        panning = false
                        settlePan()
                    }
                }
            }
        }
        .pointerInput(videoState) {
            detectDragGestures(
                onDragStart = { onPanActiveChange(true) },
                onDragEnd = { settlePan() },
                onDragCancel = { settlePan() }
            ) { change, drag ->
                if (contentOverflows(curMode, curContainer, curNative)) {
                    change.consume()
                    pan(drag.x, drag.y)
                }
            }
        }
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
