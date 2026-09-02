package com.woka.feature.viewer.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.woka.core.ui.theme.Theme
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The presentational pieces of [SubtitleCustomizePanel] — its section labels and
 * the reset row — split out so the panel itself reads as the list of settings it
 * offers (§11). Same split as `PlayerSpeedSliderParts.kt` in this package.
 */
@Composable
internal fun SubtitleFieldLabel(text: String) {
    BasicText(
        text = text.uppercase(),
        style = TextStyle(
            color = Theme.viewer.onScrimDisabled,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    )
}

@Composable
internal fun SubtitleResetRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clip(RoundedCornerShape(7.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = "Reset to defaults",
            style = TextStyle(
                color = Theme.viewer.onScrimSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
