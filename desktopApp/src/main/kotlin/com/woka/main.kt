package com.woka

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.woka.app.di.initKoin
import java.awt.Point
import com.woka.app.App
import java.awt.Dimension

fun main(args: Array<String>) {
    // Resolves the launch route too, before the first composition: the router
    // publishes its stack as a StateFlow, and a screen changed underneath a live
    // composition would animate as a navigation the user did not make.
    initKoin(args.toList())
    application {
        val windowState = rememberWindowState(
            size = DpSize(1100.dp, 720.dp),
            position = WindowPosition(Alignment.Center)
        )
        Window(
            onCloseRequest = ::exitApplication,
            title = "Woka",
            undecorated = true,
            transparent = true,
            state = windowState
        ) {
            window.minimumSize = Dimension(800, 580)
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
                            modifier = Modifier.titleBarWindowDrag(
                                window = window,
                                isMaximized = { windowState.placement == WindowPlacement.Maximized },
                                onDoubleClick = { toggleMaximize() },
                                onRestoreUnderCursor = { mouse -> restoreUnderCursor(mouse) }
                            )
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
