package io.iris

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.iris.di.initKoin

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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                App(
                    topBarArea = { content ->
                        WindowDraggableArea(content = content)
                    },
                    onCloseWindow = { exitApplication() },
                    onMinimizeWindow = { window.isMinimized = true },
                    onMaximizeWindow = {
                        window.placement = if (window.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
                    },
                    isMaximized = windowState.placement == WindowPlacement.Maximized
                )
            }
        }
    }
}