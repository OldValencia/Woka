package com.woka.feature.editor.shell

import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.woka.core.ui.theme.Theme

/**
 * The one affirmative action, so the one filled control (`#done`).
 *
 * Its label is **dark**. The accent is `#FF9F0A`: white on it measures 2.06 : 1 and
 * near-black 8.28 (ME-102), so a light label here would be the least readable text on the
 * screen rather than the most.
 */
@Composable
internal fun DoneButton(onDone: () -> Unit, choices: DoneChoices = DoneChoices(emptyList(), {})) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val room = Theme.room
    Box(
        modifier = Modifier
            .height(EditorTokens.TopBar.controlHeight)
            .clip(RoundedCornerShape(EditorTokens.TopBar.doneRadius))
            .background(if (hovered) room.accentHover else room.accent)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onDone)
            .padding(EditorTokens.TopBar.donePadding)
            .testTag("editor-done"),
        contentAlignment = Alignment.Center
    ) {
        // **A film that cannot keep the edit** (ME-289) gets the render in `Done`'s place;
        // [onDone] already renders.
        BasicText(
            text = choices.label,
            style = TextStyle(
                color = room.onAccent,
                fontSize = EditorTokens.TopBar.labelSize,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
