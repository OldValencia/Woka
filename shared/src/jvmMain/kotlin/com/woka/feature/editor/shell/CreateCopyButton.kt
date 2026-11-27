package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip

/**
 * `Create a copy…` beside `Done` (RD-401, `#output`): a quiet pill — transparent, a 1 dp ring
 * in the room's line, the room's text — so `Done` stays the one filled control. The tip says
 * what the copy is for.
 */
@Composable
internal fun CreateCopyButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val room = Theme.room
    val shape = RoundedCornerShape(EditorTokens.TopBar.doneRadius)
    ViewerTooltip("Make a separate file with your edits applied, ready for other apps.") {
        Box(
            modifier = Modifier
                .height(EditorTokens.TopBar.controlHeight)
                .clip(shape)
                .background(if (hovered) room.hoverStrong else Color.Transparent)
                .border(1.dp, room.line, shape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 14.dp)
                .testTag("editor-create-copy"),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "Create a copy…",
                style = TextStyle(color = room.text, fontSize = EditorTokens.TopBar.labelSize)
            )
        }
    }
}
