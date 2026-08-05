package com.woka

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window

/**
 * Makes an undecorated window's top bar behave like a native one: drag to move,
 * double-click to maximize, and drag-off-maximized to restore under the cursor.
 * Split out of `main.kt` (§11) — it is the only real logic in that file, and it is
 * about window behaviour rather than app startup.
 *
 * The AWT pointer location is polled rather than taken from the Compose event: the
 * event's coordinates are relative to the window being moved, so using them would
 * feed the drag its own output.
 *
 * [onRestoreUnderCursor] returns the restored window's new top-left corner, which
 * becomes the anchor for the rest of the drag; [isMaximized] is a lambda because
 * the placement changes underneath this gesture.
 */
fun Modifier.titleBarWindowDrag(
    window: Window,
    isMaximized: () -> Boolean,
    onDoubleClick: () -> Unit,
    onRestoreUnderCursor: (Point) -> Point
): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        var lastPressTime = 0L
        var windowAnchor: Point? = null
        var mouseAnchor: Point? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            when (event.type) {
                PointerEventType.Press -> {
                    if (event.changes.any { it.isConsumed }) {
                        // Press landed on a button/breadcrumb — not a title bar gesture
                        windowAnchor = null
                        mouseAnchor = null
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastPressTime < 300) {
                            lastPressTime = 0L
                            onDoubleClick()
                        } else {
                            lastPressTime = now
                        }
                        windowAnchor = window.location
                        mouseAnchor = MouseInfo.getPointerInfo()?.location
                    }
                }

                PointerEventType.Move -> {
                    val mAnchor = mouseAnchor
                    val wAnchor = windowAnchor
                    val mouse = MouseInfo.getPointerInfo()?.location
                    if (mAnchor != null && wAnchor != null && mouse != null &&
                        event.changes.any { it.pressed }
                    ) {
                        if (isMaximized()) {
                            val dx = mouse.x - mAnchor.x
                            val dy = mouse.y - mAnchor.y
                            if (dx * dx + dy * dy > 25) {
                                // Re-anchor so the continuing drag moves the
                                // restored window instead of the maximized origin
                                windowAnchor = onRestoreUnderCursor(mouse)
                                mouseAnchor = mouse
                            }
                        } else {
                            window.setLocation(
                                wAnchor.x + (mouse.x - mAnchor.x),
                                wAnchor.y + (mouse.y - mAnchor.y)
                            )
                        }
                    }
                }

                PointerEventType.Release -> {
                    windowAnchor = null
                    mouseAnchor = null
                }
            }
        }
    }
}
