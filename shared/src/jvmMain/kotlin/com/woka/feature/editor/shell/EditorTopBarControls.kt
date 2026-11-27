package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * The plain controls of the editor's header (RD-201, `editor.css` `.edit-history`,
 * `.header-actions`): muted at rest, the room's text on its strong hover, faint when there is
 * nothing to do. `Done` is `DoneButton.kt`'s and `Original` is `EditorPeekButton.kt`'s.
 *
 * **`Theme.room`, not `Theme.colors`.** The header shares the editor's room — the window
 * dims to it while the editor is open — so the app's own text tones would sit on the wrong
 * background.
 */
@Composable
internal fun TopBarButton(
    label: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TopBarFrame(tag, enabled, onClick, Modifier.padding(horizontal = EditorTokens.TopBar.textPadding)) { tint ->
        BasicText(
            text = label,
            style = TextStyle(color = tint, fontSize = EditorTokens.TopBar.labelSize)
        )
    }
}

/** A 32.dp square with an 18.dp glyph; the tooltip is the only place it says what it does. */
@Composable
internal fun TopBarIconButton(
    icon: DrawableResource,
    description: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ViewerTooltip(description) {
        TopBarFrame(tag, enabled, onClick, Modifier.size(EditorTokens.TopBar.controlHeight)) { tint ->
            Image(
                painter = painterResource(icon),
                contentDescription = description,
                modifier = Modifier.size(EditorTokens.TopBar.glyphSize),
                colorFilter = ColorFilter.tint(tint)
            )
        }
    }
}

/** `?` (`#help`, RD-401): the 32.dp square with a 14 sp semibold glyph instead of an icon. */
@Composable
internal fun TopBarGlyphButton(glyph: String, description: String, tag: String, onClick: () -> Unit) {
    ViewerTooltip(description) {
        TopBarFrame(tag, true, onClick, Modifier.size(EditorTokens.TopBar.controlHeight)) { tint ->
            BasicText(glyph, style = TextStyle(color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun TopBarFrame(
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    shape: Modifier,
    content: @Composable (tint: Color) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val room = Theme.room
    val tint = when {
        !enabled -> room.faint
        hovered -> room.text
        else -> room.muted
    }
    Box(
        modifier = Modifier
            .height(EditorTokens.TopBar.controlHeight)
            .clip(RoundedCornerShape(EditorTokens.TopBar.controlRadius))
            .background(if (enabled && hovered) room.hoverStrong else Color.Transparent)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            // Clickable while disabled too, so the semantics say "not enabled" rather than nothing.
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .then(shape)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        content(tint)
    }
}
