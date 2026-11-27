package com.woka.core.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource

/**
 * The icon every Woka window carries. Without one AWT gives a window Java's own cup, which
 * Windows shows in the taskbar preview and Alt+Tab even though the taskbar button itself
 * reads the executable's icon (found by the author on the main window and the PiP, 2026-11-27).
 */
@Composable
fun appWindowIcon(): Painter = painterResource(Icons.Static.AppIcon)
