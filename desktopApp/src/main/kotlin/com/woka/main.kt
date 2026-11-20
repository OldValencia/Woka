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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.woka.app.di.initKoin
import com.woka.app.shell.AppExitRequest
import com.woka.app.shell.ExitWhenQuiet
import com.woka.app.shell.WindowKeys
import com.woka.core.data.WokaStorage
import com.woka.core.system.FolderContextMenu
import com.woka.core.system.WindowsCommandLine
import com.woka.core.system.StartupTiming
import java.awt.Point
import com.woka.app.App
import java.awt.Dimension

fun main(rawArgs: Array<String>) {
    NativeImageOptions.apply()
    StartupTiming.mark("main")
    StartupPrewarm.start()
    val args = WindowsCommandLine.resolveArgs(rawArgs).toTypedArray()
    if (!SingleInstance.startOrForward(args)) {
        return
    }
    StartupTiming.mark("ipc")

    if (FolderContextMenu.isSupported() && FolderContextMenu.executablePath() != null) {
        FolderContextMenu.firstRunRegisterIfNeeded(WokaStorage.root)
    }

    // Resolves the launch route too, before the first composition: the router
    // publishes its stack as a StateFlow, and a screen changed underneath a live
    // composition would animate as a navigation the user did not make.
    initKoin(args.toList())
    StartupPrewarm.media(args)
    StartupTiming.mark("di")
    application {
        val windowState = rememberWindowState(
            size = defaultWindowSize(),
            position = WindowPosition(Alignment.Center)
        )
        // Neither door closes anything itself: both raise the request, and the gate
        // below decides when — after an open editor has answered for its unsaved work
        // (ME-160) and after any running save has landed (ME-125).
        Window(
            onCloseRequest = { AppExitRequest.request() },
            title = "Woka",
            undecorated = true,
            transparent = true,
            state = windowState,
            onPreviewKeyEvent = { WindowKeys.handle(it) }
        ) {
            LaunchedEffect(window) {
                SingleInstance.bindWindow(window)
                StartupPrewarm.windowShown()
            }

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
                modifier = Modifier.fillMaxSize().then(StartupTiming.shellModifier())
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
                    onCloseWindow = { AppExitRequest.request() },
                    onMinimizeWindow = { window.isMinimized = true },
                    onMaximizeWindow = { toggleMaximize() },
                    isMaximized = windowState.placement == WindowPlacement.Maximized
                )
            }
            ExitWhenQuiet(AppExitRequest.requested) {
                SingleInstance.stop()
                exitApplication()
            }
        }
    }
}
