package io.iris

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.iris.di.initKoin
import java.awt.MouseInfo
import java.awt.Point

fun main() {
    initKoin()
    application {
        val windowState = rememberWindowState(
            size = DpSize(1100.dp, 720.dp),
            position = WindowPosition(Alignment.Center)
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "Iris",
            undecorated = true,
            transparent = true,
            state = windowState
        ) {
            window.minimumSize = java.awt.Dimension(800, 580)
            val density = LocalDensity.current.density

            // The floating size must be remembered explicitly: while the window is
            // maximized, windowState.size may reflect the screen size, and we need
            // the restored width to place the window under the cursor.
            var lastFloatingSize by remember { mutableStateOf(windowState.size) }
            LaunchedEffect(windowState) {
                snapshotFlow { windowState.placement to windowState.size }
                    .collect { (placement, size) ->
                        if (placement == WindowPlacement.Floating) lastFloatingSize = size
                    }
            }

            fun toggleMaximize() {
                windowState.placement =
                    if (windowState.placement == WindowPlacement.Maximized) WindowPlacement.Floating
                    else WindowPlacement.Maximized
            }

            // Restores the window from maximized and places it so the cursor sits
            // on the title bar. Returns the new top-left corner for drag re-anchoring.
            fun restoreUnderCursor(mouse: Point): Point {
                val widthPx = (lastFloatingSize.width.value * density).toInt()
                val x = mouse.x - widthPx / 2
                val y = (mouse.y - (20 * density).toInt()).coerceAtLeast(0)
                windowState.placement = WindowPlacement.Floating
                window.placement = WindowPlacement.Floating
                window.setLocation(x, y)
                windowState.position = WindowPosition((x / density).dp, (y / density).dp)
                return Point(x, y)
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                App(
                    topBarArea = { content ->
                        Box(
                            modifier = Modifier.pointerInput(Unit) {
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
                                                        toggleMaximize()
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
                                                    if (windowState.placement == WindowPlacement.Maximized) {
                                                        val dx = mouse.x - mAnchor.x
                                                        val dy = mouse.y - mAnchor.y
                                                        if (dx * dx + dy * dy > 25) {
                                                            // Re-anchor so the continuing drag moves the
                                                            // restored window instead of the maximized origin
                                                            windowAnchor = restoreUnderCursor(mouse)
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
                        ) {
                            content()
                        }
                    },
                    onCloseWindow = { exitApplication() },
                    onMinimizeWindow = { window.isMinimized = true },
                    onMaximizeWindow = { toggleMaximize() },
                    isMaximized = windowState.placement == WindowPlacement.Maximized
                )
            }
        }
    }
}
