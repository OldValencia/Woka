package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/**
 * Bucket B's one extra thing: this format has nowhere to keep the edit, so `Done`
 * re-encodes the picture instead of recording a recipe beside it (§5.1).
 *
 * **Not a modal, and deliberately not a confirmation.** A warning that costs a click
 * has been turned into a step (§4.5); this is shown for the whole session so the cost
 * is known before the work rather than after it. It follows [EditorSkippedNotice] in
 * shape and placement for the same reason that one stands outside the inspector — a
 * notice reachable only by opening a tool is a notice nobody is guaranteed to see.
 *
 * It says what happens rather than what is missing: "re-encoded in place" is the fact
 * the user can act on, and "there is no revert" is its consequence.
 */
@Composable
internal fun EditorNoHistoryNotice() {
    val warn = Theme.colors.accentFolders
    Column(
        modifier = Modifier
            .widthIn(min = EditorTokens.Inspector.minWidth, max = EditorTokens.Inspector.maxWidth)
            .clip(RoundedCornerShape(9.dp))
            .background(warn.copy(alpha = 0.14f))
            .border(1.dp, warn.copy(alpha = 0.32f), RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp)
            .testTag("editor-no-history-notice"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BasicText(
            text = "This format keeps no edit history",
            style = TextStyle(color = warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            text = "Done re-encodes the picture in place. There is nothing to revert to " +
                "afterwards, and editing it again starts from the re-encoded file.",
            style = TextStyle(color = Theme.viewer.onScrimSecondary, fontSize = 11.5.sp)
        )
    }
}
