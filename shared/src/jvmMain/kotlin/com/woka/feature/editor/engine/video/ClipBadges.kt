package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
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
 * own (ME-215), and its speed when it is not 1× (ME-216) — a narrow tile has to say *why*
 * it is narrow.
 */
@Composable
internal fun BoxScope.ClipBadges(ownLook: Boolean, speed: Float) {
    if (ownLook) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(6.dp)
                .background(Theme.colors.accentEditor, CircleShape)
        )
    }
    if (speed != 1f) {
        BasicText(
            text = speedLabel(speed),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(3.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            style = TextStyle(color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

/**
 * `¼×`, `½×`, `1.25×`, `2×` — the vulgar fractions read faster than `0.25×` on a 28 dp tile,
 * and everything else is the viewer's own `PlaybackSpeed.label`: two decimals, zeroes trimmed.
 */
internal fun speedLabel(speed: Float): String = when (speed) {
    0.25f -> "¼×"
    0.5f -> "½×"
    else -> PlaybackSpeed.label(speed)
}
