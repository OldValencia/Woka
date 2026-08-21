package com.woka.feature.editor.shell

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.viewer.shell.ZOOM_STEP_RATIO

/**
 * The three pointer gestures the canvas answers, split out of [EditorCanvasHost] under
 * the size mandate when the click arrived.
 *
 * **It still names no bitmap and no player.** The click asks the engine to start or
 * stop whatever moves on it — a frame clock for an animated still, a player for the
 * video editor at 5b — and a photograph's engine does nothing with it, so the gesture
 * costs the common case a call that returns.
 *
 * [onZoomAt] is handed the new zoom and the pointer it happened under, because the pan
 * that keeps that point still is the caller's to apply along with the zoom itself.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.editorCanvasGestures(
    engine: EditorEngine,
    geometry: EditorCanvasGeometry,
    zoom: Float,
    onZoomAt: (Float, Offset) -> Unit,
    onPan: (Offset) -> Unit
): Modifier = this
    .onPointerEvent(PointerEventType.Scroll) { event ->
        val change = event.changes.firstOrNull() ?: return@onPointerEvent
        val notches = change.scrollDelta.y
        if (notches == 0f) return@onPointerEvent
        // Scrolling *up* is negative, and up means closer. The notch is the viewer's
        // own, so one turn of the wheel moves the picture by the same amount in both.
        val next = (zoom * if (notches < 0f) ZOOM_STEP_RATIO else 1f / ZOOM_STEP_RATIO)
            .coerceIn(geometry.minZoom, geometry.maxZoom)
        if (next != zoom) onZoomAt(next, change.position)
        change.consume()
    }
    .pointerInput(engine) {
        // The same gesture means the same thing one panel below, in the viewer.
        detectTapGestures { engine.togglePlayback() }
    }
    .pointerInput(Unit) {
        // Any drag pans. There is no competing gesture on the canvas until a
        // `CanvasTool` exists (ME-109), and at that point the overlay gets first
        // refusal and this becomes the fallback.
        detectDragGestures { change, dragged ->
            onPan(dragged)
            change.consume()
        }
    }
