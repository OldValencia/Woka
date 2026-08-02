package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.ui.theme.Theme

/** Preset text colours offered in the subtitle customizer (0xAARRGGBB). */
val subtitleColorSwatches = listOf(
    0xFFFFFFFFL, 0xFFFFF04DL, 0xFF4DD2FFL, 0xFF6EE86EL, 0xFFFF7AB2L, 0xFFFF5A4DL
)

/** Compact dark segmented control matching the subtitle popover style. */
@Composable
fun <T> SubtitlePillGroup(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isSelected) Theme.colors.accent.copy(alpha = 0.30f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = Color.White.copy(alpha = if (isSelected) 1f else 0.7f),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun SubtitleColorSwatchRow(selectedArgb: Long, onSelect: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        subtitleColorSwatches.forEach { argb ->
            val isSelected = argb == selectedArgb
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(argb.toSubtitleColor())
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Theme.colors.accent else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onSelect(argb) }
            )
        }
    }
}

/** Dark −/+ stepper for a 0–100% value (used for background box opacity). */
@Composable
fun SubtitlePercentStepper(percent: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperGlyph("−") { onChange((percent - 10).coerceAtLeast(0)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = "$percent%",
                style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            )
        }
        StepperGlyph("+") { onChange((percent + 10).coerceAtMost(100)) }
    }
}

@Composable
private fun StepperGlyph(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(34.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}
