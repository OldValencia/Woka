package com.woka.feature.viewer.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.Marquee
import com.woka.core.ui.theme.Theme

/**
 * Selectable row of a toolbar popover list, in the §2.1 / §2.2 style: accent fill
 * when selected, subtle fill on hover, back-and-forth marquee for labels too long
 * to fit.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PopoverListRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    var hovered by remember { mutableStateOf(false) }
    val background = when {
        selected -> Theme.colors.accent.copy(alpha = 0.30f)
        hovered -> Color.White.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Marquee(
            text = label,
            style = TextStyle(
                color = Color.White.copy(alpha = if (selected) 1f else 0.72f),
                fontSize = 12.sp
            ),
            animate = hovered || selected,
            modifier = Modifier.weight(1f)
        )
        // Only appears on hover, and only for rows that can actually be removed,
        // so the list stays as quiet as the rest of the player's popovers.
        if (onRemove != null && hovered) {
            RemoveRowButton(onRemove)
        }
    }
}

/** Small ✕ at the trailing edge of a removable row. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RemoveRowButton(onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✕",
            color = Color.White.copy(alpha = if (hovered) 1f else 0.6f),
            style = TextStyle(fontSize = 10.sp)
        )
    }
}

/** Small uppercase group heading above a [PopoverListRow] block. */
@Composable
internal fun PopoverSectionLabel(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.45f),
        style = TextStyle(fontSize = 10.sp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
