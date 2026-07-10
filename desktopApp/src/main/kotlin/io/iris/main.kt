package io.iris

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import io.iris.di.initKoin
import io.iris.ui.theme.IrisTheme

fun main() {
    initKoin()
    application {
        val windowState = rememberWindowState()
        Window(
            onCloseRequest = ::exitApplication,
            title = "Iris",
            undecorated = true,
            state = windowState
        ) {
            IrisTheme {
                Column(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background)) {
                    WindowDraggableArea {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(IrisTheme.colors.surface),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(16.dp))
                            BasicText("Iris", style = TextStyle(color = IrisTheme.colors.textPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                            Spacer(Modifier.weight(1f))
                            Box(modifier = Modifier.size(40.dp, 32.dp).clickable { window.isMinimized = true }, contentAlignment = Alignment.Center) {
                                BasicText("—", style = TextStyle(color = IrisTheme.colors.textPrimary))
                            }
                            Box(modifier = Modifier.size(40.dp, 32.dp).clickable { 
                                window.placement = if (window.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
                            }, contentAlignment = Alignment.Center) {
                                BasicText("□", style = TextStyle(color = IrisTheme.colors.textPrimary))
                            }
                            Box(modifier = Modifier.size(40.dp, 32.dp).clickable { exitApplication() }, contentAlignment = Alignment.Center) {
                                BasicText("✕", style = TextStyle(color = IrisTheme.colors.textPrimary))
                            }
                        }
                    }
                    App()
                }
            }
        }
    }
}