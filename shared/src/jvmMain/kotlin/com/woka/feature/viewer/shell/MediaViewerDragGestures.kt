package com.woka.feature.viewer.shell

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The raw pointer loop behind the media area: `Ctrl + Scroll` zooms one geometric
 * step around the cursor, and a held button pans while the content overflows.
 * Split out of [mediaViewerGestures] per the §2 size mandate, along the boundary
 * [mediaTapGestures] already uses.
 *
 * The loop runs on [PointerEventPass.Initial] so the Ctrl pan is intercepted before
 * the child `ContextMenuArea`; everything it does not want is left unconsumed and
 * still reaches the context menu and the tap detector on the Main pass.
 *
 * [ctrlPrimaryPan] adds `Ctrl + LMB` to the panning buttons. It is on only in the
 * PiP/fullscreen window, where a plain left drag belongs to `pipWindowDraggable`
 * and has to move the window rather than the picture (TC-PIP-02).
 */
internal fun Modifier.mediaZoomAndButtonPan(
    mode: () -> SizeMode,
    container: () -> IntSize,
    native: () -> IntSize,
    ctrlPrimaryPan: Boolean,
    onZoom: (Int, Offset) -> Unit,
    onPan: (Float, Float) -> Unit,
    onPanStart: () -> Unit,
    onPanEnd: () -> Unit
): Modifier = this.pointerInput(ctrlPrimaryPan) {
    awaitPointerEventScope {
        var panning = false
        var lastPos = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.first()
            val isCtrl = event.keyboardModifiers.isCtrlPressed ||
                event.keyboardModifiers.isMetaPressed

            if (event.type == PointerEventType.Scroll && isCtrl) {
                val delta = change.scrollDelta.y
                val bounds = zoomBounds(container(), native())
                var percent = mode().percentIn(container(), native())
                // Trackpads report several units in one event; repeat the
                // geometric step rather than scaling a flat one.
                repeat(abs(delta).roundToInt().coerceIn(1, 3)) {
                    percent = if (delta > 0) {
                        stepDown(percent, bounds)
                    } else {
                        stepUp(percent, bounds)
                    }
                }
                onZoom(percent, change.position)
                event.changes.forEach { it.consume() }
                continue
            }

            val panButton = event.buttons.isSecondaryPressed ||
                (ctrlPrimaryPan && event.buttons.isPrimaryPressed)
            // **No overflow gate** (asked for 2026-08-28, found by instrumenting the
            // loop). It used to refuse unless the media was larger than the panel,
            // which made the gesture dead below the fit percentage — measured at
            // 75 % for a 1518×860 picture in a 1100×660 panel. How far the drag may
            // go is `panLimits`' question, not this one's, and it now answers with
            // room at every zoom.
            if (isCtrl && panButton) {
                if (!panning) {
                    panning = true
                    lastPos = change.position
                    onPanStart()
                } else {
                    val moved = change.position - lastPos
                    lastPos = change.position
                    onPan(moved.x, moved.y)
                }
                change.consume()
            } else if (panning) {
                panning = false
                onPanEnd()
            }
        }
    }
}
