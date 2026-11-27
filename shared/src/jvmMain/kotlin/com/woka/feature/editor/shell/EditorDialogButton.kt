package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.woka.core.ui.theme.Theme

/** The prototype's three dialog buttons (`editor.css`): the orange pill, a well, and plain text. */
internal enum class DialogButtonKind { Primary, Secondary, Quiet }

/**
 * One dialog button (RD-401). **Primary** is the editor's orange with dark text, as `Done`
 * (answer 6); **Secondary** a well that darkens on hover; **Quiet** the room's muted text.
 * Disabled is the prototype's 35 % — still in the semantics, so a test can ask it.
 */
@Composable
internal fun EditorDialogButton(
    label: String,
    tag: String,
    kind: DialogButtonKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val tokens = EditorDialogTokens.Button
    val room = Theme.room
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val live = enabled && hovered
    val shape = RoundedCornerShape(
        when (kind) {
            DialogButtonKind.Primary -> tokens.primaryRadius
            DialogButtonKind.Secondary -> tokens.secondaryRadius
            DialogButtonKind.Quiet -> tokens.radius
        }
    )
    val fill = when (kind) {
        DialogButtonKind.Primary -> if (live) room.accentHover else room.accent
        DialogButtonKind.Secondary -> if (live) room.well2 else room.well
        DialogButtonKind.Quiet -> if (live) room.hover else Color.Transparent
    }
    val text = when (kind) {
        DialogButtonKind.Primary -> room.onAccent
        DialogButtonKind.Secondary -> room.text
        DialogButtonKind.Quiet -> if (live) room.text else room.muted
    }
    val padding = when {
        kind != DialogButtonKind.Primary -> tokens.padding
        compact -> tokens.compactPrimaryPadding
        else -> tokens.primaryPadding
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = tokens.height)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(fill)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(padding)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = text,
                fontSize = tokens.labelSize,
                fontWeight = if (kind == DialogButtonKind.Primary) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}
