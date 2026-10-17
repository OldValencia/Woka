package com.woka.app.shell.sidebar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The pieces every sidebar row is built from: the hover-and-select frame with its
 * right-click, the tinted glyph and the one-line label. Split from `SidebarRow` under the
 * size mandate; the folder, pinned and foot rows all draw through them.
 */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SidebarRowFrame(
    selected: Boolean,
    height: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    onSecondaryClick: (IntOffset) -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val background = when {
        selected -> Theme.colors.accentSoft
        hovered -> Theme.colors.surfaceHover
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(SidebarTokens.RowRadius))
            .background(background)
            .hoverable(source)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            // Secondary press only; the primary button is left to `clickable`.
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.button != PointerButton.Secondary) return@onPointerEvent
                val change = event.changes.first()
                val at = change.position
                onSecondaryClick(IntOffset(at.x.toInt() + 2, at.y.toInt() + 2))
                change.consume()
            }
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = SidebarTokens.RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SidebarTokens.RowPadding),
        content = content
    )
}

@Composable
internal fun RowIcon(
    selected: Boolean,
    tint: Color,
    icon: DrawableResource = Icons.Static.Folder
) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(SidebarTokens.IconSize),
        colorFilter = ColorFilter.tint(if (selected) Theme.colors.accent else tint)
    )
}

@Composable
internal fun RowLabel(
    text: String,
    modifier: Modifier,
    color: Color,
    size: TextUnit = SidebarTokens.LabelSize
) {
    BasicText(
        text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontSize = size)
    )
}

/** `.sec`: the small grey label over a group of rows. */
@Composable
internal fun SectionLabel(text: String) {
    BasicText(
        text,
        modifier = Modifier.padding(
            start = SidebarTokens.RowPadding,
            top = SidebarTokens.SectionTop,
            bottom = 5.dp
        ),
        style = TextStyle(
            color = Theme.colors.textTertiary,
            fontSize = SidebarTokens.SectionSize,
            fontWeight = FontWeight.SemiBold
        )
    )
}
