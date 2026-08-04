package com.woka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.ui.theme.Theme

/** Steps in five-minute increments, clamped to [5, 240]. */
private const val STEP = 5
private const val MIN_MINUTES = 5
private const val MAX_MINUTES = 240

/**
 * `[−]  20 min  [+]` in the settings-panel visual language. Split out of
 * [VideoPlayerSettingsPanel] per the §2 file-size mandate.
 */
@Composable
internal fun MinutesStepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Theme.colors.surfacePressed),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperGlyph("−") { onChange((minutes - STEP).coerceAtLeast(MIN_MINUTES)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BasicText(
                text = "$minutes min",
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        StepperGlyph("+") { onChange((minutes + STEP).coerceAtMost(MAX_MINUTES)) }
    }
}

@Composable
private fun StepperGlyph(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = symbol,
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
