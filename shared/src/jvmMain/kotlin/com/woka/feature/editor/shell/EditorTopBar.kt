package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * The editor's accessory bar: leave, what you are editing, what you did, finish.
 *
 * Nothing here decides *what* `Done` does — the save-target menu is ME-104's, and a
 * menu whose `Overwrite` entry did nothing would be worse than a button. Both actions
 * are plain callbacks, so the same bar serves the video editor unchanged.
 */
@Composable
internal fun EditorTopBar(
    fileName: String,
    isDirty: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditorTokens.TopBar.height)
            .background(Theme.viewer.hudScrim)
            .padding(horizontal = EditorTokens.TopBar.sidePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TopBarButton(label = "Cancel", tag = "editor-cancel", onClick = onCancel)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicText(
                text = fileName,
                style = TextStyle(
                    color = Theme.viewer.onScrim,
                    fontSize = EditorTokens.TopBar.titleStyle,
                    fontWeight = FontWeight.SemiBold
                )
            )
            // Only the mark, never a second word: "Edited" beside a name the user is
            // looking at says what the dot says, in eight times the space.
            if (isDirty) {
                BasicText(
                    text = "· Edited",
                    modifier = Modifier.testTag("editor-dirty"),
                    style = TextStyle(
                        color = Theme.viewer.onScrimSecondary,
                        fontSize = EditorTokens.TopBar.subtitleStyle
                    )
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EditorTokens.TopBar.controlGap)
        ) {
            TopBarButton("↶", "editor-undo", enabled = canUndo, onClick = onUndo)
            TopBarButton("↷", "editor-redo", enabled = canRedo, onClick = onRedo)
            DoneButton(onDone)
        }
    }
}
