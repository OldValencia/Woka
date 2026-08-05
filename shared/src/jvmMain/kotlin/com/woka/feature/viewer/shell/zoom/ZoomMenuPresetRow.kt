package com.woka.feature.viewer.shell.zoom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.ScalePreset
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

internal fun presetLabel(preset: ScalePreset) = when (preset) {
    ScalePreset.Fit -> "Fit"
    ScalePreset.Fill -> "Fill"
    ScalePreset.Original -> "Actual"
}

internal fun presetIcon(preset: ScalePreset) = when (preset) {
    ScalePreset.Fit -> Icons.Static.ScaleFit
    ScalePreset.Fill -> Icons.Static.ScaleFill
    ScalePreset.Original -> Icons.Static.ScaleOriginal
}

/**
 * One snap point in the zoom menu: name, icon, and the percentage it resolves to
 * for the current panel — the number is what makes the scale and the presets read
 * as the same axis rather than two separate concepts.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ZoomPresetRow(
    preset: ScalePreset,
    percent: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    val tint = if (selected) Theme.colors.accentFolders else Theme.colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(
                when {
                    selected -> Theme.colors.accentFolders.copy(alpha = 0.12f)
                    hovered -> Theme.colors.textPrimary.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(presetIcon(preset)),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = presetLabel(preset),
            style = TextStyle(
                color = tint,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        Text(
            text = "$percent%",
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 12.sp)
        )
    }
}
