package com.woka.feature.viewer.shell.zoom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/** Manual entry plus the reset action, on one row at the foot of the zoom menu. */
@Composable
internal fun ZoomMenuInputRow(
    percent: Int,
    bounds: IntRange,
    canReset: Boolean,
    onPercent: (Int) -> Unit,
    onReset: () -> Unit,
    onCommit: () -> Unit
) {
    var text by remember { mutableStateOf(percent.toString()) }
    // Keep the field honest when the value moves from the slider, wheel or a preset.
    LaunchedEffect(percent) {
        if (text.toIntOrNull() != percent) text = percent.toString()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .width(84.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Theme.colors.surfaceElevated.copy(alpha = 0.6f))
                .border(1.dp, Theme.colors.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(4)
                    text = digits
                    digits.toIntOrNull()?.let { if (it in bounds) onPercent(it) }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(Theme.colors.textPrimary),
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (enter && event.type == KeyEventType.KeyDown) {
                        onCommit()
                        true
                    } else {
                        false
                    }
                }
            )
            Text(
                text = "%",
                style = TextStyle(color = Theme.colors.textSecondary, fontSize = 13.sp)
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .then(if (canReset) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .clickable(enabled = canReset) { onReset() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Reset",
                style = TextStyle(
                    color = if (canReset) Theme.colors.accentFolders
                    else Theme.colors.textSecondary.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
