package com.woka.feature.editor.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.woka.core.ui.components.ORIGINAL_TOOLTIP
import com.woka.core.ui.components.OriginalCompare
import com.woka.core.ui.components.originalPress
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource

/**
 * `Original` — the one control in the editor that changes nothing (ME-173, RD-201).
 *
 * **A click toggles, a hold peeks** (RD-502), the same control as the viewer's — the
 * gesture is [originalPress]'s. Until RD-502 it was hold-only: a comparison that ended
 * when the pointer lifted could not be looked at while a slider moved, and the viewer's,
 * drawn as a toggle, would have taught the opposite of this one.
 *
 * **It is not a tool**, so it is not a tab: no inspector, no parameters, no stage (§3.4).
 * It sits in the header's right group with the other controls that only change *what you
 * see*, icon and word (`#compare` in the prototype); on, it lights in the room's accent.
 */
@Composable
internal fun EditorPeekButton(original: OriginalCompare) {
    val peeking = original.active
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val room = Theme.room
    val tint = when {
        peeking -> room.accentText
        hovered -> room.text
        else -> room.muted
    }
    ViewerTooltip(ORIGINAL_TOOLTIP) {
        Row(
            modifier = Modifier
                .height(EditorTokens.TopBar.controlHeight)
                .clip(RoundedCornerShape(EditorTokens.TopBar.controlRadius))
                .background(
                    when {
                        peeking -> room.accentFill
                        hovered -> room.hoverStrong
                        else -> Color.Transparent
                    }
                )
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .originalPress(original)
                .padding(horizontal = EditorTokens.TopBar.originalPadding)
                .testTag("editor-peek-original"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EditorTokens.TopBar.originalGap)
        ) {
            Image(
                painter = painterResource(Icons.Static.PeekOriginal),
                contentDescription = null,
                modifier = Modifier.size(EditorTokens.TopBar.glyphSize),
                colorFilter = ColorFilter.tint(tint)
            )
            BasicText("Original", style = TextStyle(color = tint, fontSize = EditorTokens.TopBar.labelSize))
        }
    }
}
