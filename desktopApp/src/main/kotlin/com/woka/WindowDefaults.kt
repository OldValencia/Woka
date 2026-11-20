package com.woka

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment

/**
 * The window a first launch opens at: 80 % of the screen's usable area, no larger
 * than 1440×900 and no smaller than the 800×580 minimum (SIDE-003). It was a fixed
 * 1100×720, which with a 236.dp sidebar left the toolbar and grid cramped on any
 * screen big enough to have room to spare. AWT reports the area in the same scaled
 * units Compose calls dp, so no density conversion is needed.
 */
internal fun defaultWindowSize(): DpSize {
    val area = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val width = (area.width * 0.8f).coerceIn(800f, 1440f)
    val height = (area.height * 0.8f).coerceIn(580f, 900f)
    return DpSize(width.dp, height.dp)
}
