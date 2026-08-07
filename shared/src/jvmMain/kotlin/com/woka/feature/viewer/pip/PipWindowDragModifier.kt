package com.woka.feature.viewer.pip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Point
import kotlin.math.abs

/**
 * Screen pixels the cursor has to travel before a press counts as a window drag
 * rather than a click. Deliberately **not** `viewConfiguration.touchSlop`: that is
 * measured in the composable's own coordinates, and while this modifier is working
 * the window travels with the cursor — so the pointer barely moves locally however
 * far the window goes.
 */
private const val DRAG_SLOP_PX = 4

fun Modifier.pipWindowDraggable(window: Frame): Modifier = composed {
    var dragStartMouse by remember { mutableStateOf<Point?>(null) }
    var dragStartWin by remember { mutableStateOf<Point?>(null) }
    var didDrag by remember { mutableStateOf(false) }

    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                // Initial pass, so that once this has actually moved the window it
                // can consume ahead of the media layer's tap detector. Without
                // that the release lands as a tap: playback toggles when the
                // button comes up, and a second drag reads as a double tap and
                // drops PiP back into the app (TC-PIP-02).
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed

                if (event.type == PointerEventType.Press && !isCtrl) {
                    dragStartMouse = MouseInfo.getPointerInfo().location
                    dragStartWin = window.location
                    didDrag = false
                }

                if (event.type == PointerEventType.Move && !isCtrl && isPipSized(window)) {
                    val startMouse = dragStartMouse
                    val startWin = dragStartWin
                    if (startMouse != null && startWin != null && event.changes.any { it.pressed }) {
                        val currentMouse = MouseInfo.getPointerInfo().location
                        val dx = currentMouse.x - startMouse.x
                        val dy = currentMouse.y - startMouse.y
                        if (abs(dx) + abs(dy) > DRAG_SLOP_PX) didDrag = true
                        window.location = Point(startWin.x + dx, startWin.y + dy)
                    }
                }

                // The release is consumed too — that is the event the tap detector
                // is waiting for. [didDrag] survives it and is cleared on the next
                // press, so a click that never travelled still reaches play/pause.
                if (didDrag) event.changes.forEach { it.consume() }

                if (event.type == PointerEventType.Release) {
                    dragStartMouse = null
                    dragStartWin = null
                }
            }
        }
    }
}
