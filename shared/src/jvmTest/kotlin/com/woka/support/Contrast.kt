package com.woka.support

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG contrast arithmetic for colour-token tests (`ViewerChromeTest`, `ThemeColorsTest`).
 * A token's readability is a number, so the tests measure it rather than look at it.
 */
private fun linear(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

internal fun luminance(color: Color): Double =
    0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

/** [over] composited under [color]'s own alpha — what a scrim or a tint does to what lies below. */
internal fun flatten(color: Color, over: Color): Color {
    val a = color.alpha
    return Color(
        red = color.red * a + over.red * (1 - a),
        green = color.green * a + over.green * (1 - a),
        blue = color.blue * a + over.blue * (1 - a)
    )
}

/** The ratio between two opaque colours, always ≥ 1. */
internal fun contrast(foreground: Color, background: Color): Double {
    val a = luminance(foreground) + 0.05
    val b = luminance(background) + 0.05
    return if (a > b) a / b else b / a
}
