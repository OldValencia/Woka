package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/**
 * The card the schema draws into.
 *
 * [content] is ME-102's — this owns the header and the frame and knows nothing about
 * which controls appear inside it. That is the split §3.4 depends on: a tool
 * contributes rows, never a panel.
 *
 * The skipped-operations notice is deliberately **not** here; see [EditorSkippedNotice].
 */
@Composable
internal fun EditorInspector(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(min = EditorTokens.Inspector.minWidth, max = EditorTokens.Inspector.maxWidth)
            .clip(RoundedCornerShape(Theme.viewer.cornerRadius))
            .background(Theme.viewer.popoverScrim)
            .border(
                1.dp,
                Theme.viewer.onScrimBorder,
                RoundedCornerShape(Theme.viewer.cornerRadius)
            )
            .padding(EditorTokens.Inspector.padding)
            .testTag("editor-inspector"),
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)
    ) {
        InspectorHeader(title)
        content()
    }
}

/**
 * The tool's name, and nothing else.
 *
 * **`Reset` was removed here on 2026-08-19.** It cleared the working document — every
 * tool's operations, not the open panel's — from a header that named one tool, and
 * beside an Undo that already takes work back a step at a time. What it was reached
 * for was "put this photograph back as it was", and that is Revert, which now sits
 * beside Back where a destructive control belongs (ME-107).
 */
@Composable
private fun InspectorHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(
            text = title,
            modifier = Modifier.testTag("editor-inspector-title"),
            style = TextStyle(
                color = Theme.viewer.onScrim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
