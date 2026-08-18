package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * The two button shapes the editor puts in the shell's top bar.
 *
 * **`Theme.colors`, not `Theme.viewer.onScrim*`.** These used to live on the editor's
 * own bar, which was a scrim over the photograph; they now sit in the app's top bar,
 * which is an ordinary surface and follows the theme. Reading the on-scrim set here
 * would put near-white text on a light background.
 */

/** A plain label that reacts to hover and dims rather than disappears when disabled. */
@Composable
internal fun TopBarButton(
    label: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(EditorTokens.TopBar.controlHeight)
            .clip(RoundedCornerShape(EditorTokens.TopBar.controlRadius))
            .then(
                if (enabled) {
                    Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (enabled) Theme.colors.textPrimary else Theme.colors.textTertiary,
                fontSize = EditorTokens.TopBar.subtitleStyle,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

/**
 * The one affirmative action, so the one filled control.
 *
 * Its label is **dark**. The accent is `#FF9F0A`: white on it measures 2.06 : 1 and
 * near-black 8.28 (ME-102), so a light label here would be the least readable text on
 * the screen rather than the most.
 */
@Composable
internal fun DoneButton(onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .height(EditorTokens.TopBar.controlHeight)
            .clip(RoundedCornerShape(EditorTokens.TopBar.doneRadius))
            .background(Theme.colors.accentEditor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onDone)
            .padding(EditorTokens.TopBar.donePadding)
            .testTag("editor-done"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "Done",
            style = TextStyle(
                color = Color(0xFF1C1C1E),
                fontSize = EditorTokens.TopBar.subtitleStyle,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
