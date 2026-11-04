package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.PlaybackSpeed
import com.woka.core.ui.theme.Theme

/**
 * What a clip carries beyond its range, in its top corners: ME-157's dot for a look of its
 * own (ME-215), and its speed when it is not 1× (ME-216) or its hold when it is a freeze
 * (ME-217) — a tile has to say *why* it is the width it is.
 */
@Composable
internal fun BoxScope.ClipBadges(ownLook: Boolean, speed: Float, holdMs: Long = 0L) {
    // A freeze stands apart from film (2026-11-04: "visible, but it could be clearer"): the
    // editor's orange over its one frame, outlined, and its hold on an orange label.
    if (holdMs > 0L) {
        Box(
            Modifier.matchParentSize()
                .background(Theme.colors.accentEditor.copy(alpha = 0.22f))
                .border(1.5.dp, Theme.colors.accentEditor, RoundedCornerShape(CLIP_RADIUS))
        )
    }
    if (ownLook) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(6.dp)
                .background(Theme.colors.accentEditor, CircleShape)
        )
    }
    val label = when {
        holdMs > 0L -> "Hold ${holdMs / 1000}.${holdMs % 1000 / 100}s"
        speed != 1f -> speedLabel(speed)
        else -> null
    }
    if (label != null) {
        val freeze = holdMs > 0L
        BasicText(
            text = label,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(3.dp)
                .background(if (freeze) Theme.colors.accentEditor else Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            style = TextStyle(color = if (freeze) ON_ORANGE else Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

/** Dark on the editor's orange, as `DoneButton`'s label is. */
private val ON_ORANGE = Color(0xFF1C1C1E)

/**
 * `¼×`, `½×`, `1.25×`, `2×` — the vulgar fractions read faster than `0.25×` on a 28 dp tile,
 * and everything else is the viewer's own `PlaybackSpeed.label`: two decimals, zeroes trimmed.
 */
internal fun speedLabel(speed: Float): String = when (speed) {
    0.25f -> "¼×"
    0.5f -> "½×"
    else -> PlaybackSpeed.label(speed)
}
