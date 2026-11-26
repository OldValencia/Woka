package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A full-width well button (`.well-button`): Auto-enhance, Transform's presses, Clip's Split and
 * Delete. [danger] draws glyph and word in the room's red (`.well-button.danger`).
 */
@Composable
internal fun WellButton(
    label: String,
    icon: DrawableResource?,
    tag: String,
    modifier: Modifier = Modifier,
    accentGlyph: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val room = Theme.room
    val ink = when {
        !enabled -> room.faint
        danger -> room.danger
        else -> room.text
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled && hovered) room.well2 else room.well)
            .hoverable(hover)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        icon?.let { glyph ->
            val tint = when {
                !enabled || danger -> ink
                accentGlyph -> room.accent
                else -> room.muted
            }
            Image(painterResource(glyph), null, Modifier.size(17.dp), colorFilter = ColorFilter.tint(tint))
        }
        BasicText(label, style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = if (accentGlyph) FontWeight.SemiBold else FontWeight.Normal))
    }
}

/** A quiet line under the rows (`.text-action`): Reset adjustments, Clip's More actions. */
@Composable
internal fun TextAction(label: String, icon: DrawableResource?, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val tint = if (hovered) Theme.room.text else Theme.room.muted
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered) Theme.room.hoverStrong else Color.Transparent)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon?.let { glyph ->
            Image(painterResource(glyph), null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(tint))
        }
        BasicText(label, style = TextStyle(color = tint, fontSize = 12.sp))
    }
}
