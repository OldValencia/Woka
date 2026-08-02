package com.woka.ui.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.woka.data.SubtitleFont
import com.woka.data.SubtitleSize

/**
 * Resolves the persisted subtitle appearance (§3) into concrete render values.
 * The size is always relative to the video-panel height, so a resize keeps the
 * user's choice proportional instead of resetting it. [SubtitleSize.Auto] is the
 * neutral base; the fixed steps are multipliers of that same base.
 */
fun subtitleFontSizeSp(size: SubtitleSize, panelHeightDp: Float): Float {
    val base = (panelHeightDp * 0.045f).coerceIn(16f, 52f)
    val multiplier = when (size) {
        SubtitleSize.Auto -> 1.0f
        SubtitleSize.Small -> 0.8f
        SubtitleSize.Medium -> 1.0f
        SubtitleSize.Large -> 1.3f
        SubtitleSize.ExtraLarge -> 1.6f
    }
    return base * multiplier
}

fun SubtitleFont.toFontFamily(): FontFamily = when (this) {
    SubtitleFont.System -> FontFamily.SansSerif
    SubtitleFont.Serif -> FontFamily.Serif
    SubtitleFont.Monospace -> FontFamily.Monospace
}

/** Unpacks a persisted 0xAARRGGBB value into a Compose [Color]. */
fun Long.toSubtitleColor(): Color {
    val a = ((this shr 24) and 0xFF).toInt()
    val r = ((this shr 16) and 0xFF).toInt()
    val g = ((this shr 8) and 0xFF).toInt()
    val b = (this and 0xFF).toInt()
    return Color(r, g, b, a)
}
