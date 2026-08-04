package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.woka.ui.components.Marquee
import com.woka.ui.theme.Theme

/**
 * Accent-coloured action row at the foot of the subtitle popover. Kept separate
 * from [PopoverListRow]: it is an action, never a selection, so it has no selected
 * state and always renders in the accent colour. Split out of `SubtitlePopover.kt`
 * per the §2 file-size mandate.
 *
 * Disabled it dims to 0.4 and drops the hover affordances — matching the muted
 * speaker in `MediaViewerVolumeControl` — and [disabledHint] says *why* on hover,
 * since a greyed row on its own explains nothing.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SubtitleActionRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledHint: String? = null
) {
    var hovered by remember { mutableStateOf(false) }
    val background = if (hovered && enabled) Color.White.copy(alpha = 0.10f) else Color.Transparent
    val row: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Marquee(
                text = label,
                style = TextStyle(
                    color = Theme.colors.accent.copy(alpha = if (enabled) 1f else 0.4f),
                    fontSize = 12.sp
                ),
                animate = hovered && enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    if (enabled || disabledHint == null) {
        row()
    } else {
        SimpleTooltip(disabledHint) { row() }
    }
}
