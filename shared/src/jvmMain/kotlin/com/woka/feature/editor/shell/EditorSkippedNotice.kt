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
 * "Their edits are kept" is the sentence that matters, so it is the one written out.
 *
 * **It stands on its own rather than inside the inspector**, which is where it was
 * first put and where the shell test caught it: the inspector exists only while a tool
 * is open, so a user who switched a tool off in Settings and came back would be shown
 * nothing at all until they happened to open something unrelated. §3.4 promises a
 * document never silently loses work, and a notice reachable only by accident does not
 * keep that promise.
 *
 * The count leads and the titles follow, because an operation whose tool this build
 * does not have contributes no title — the count stays true where a list of names
 * would have a hole in it (see [skippedToolTitles]).
 */
@Composable
internal fun EditorSkippedNotice(count: Int, titles: List<String>) {
    val warn = Theme.colors.accentFolders
    Column(
        modifier = Modifier
            .widthIn(min = EditorTokens.Inspector.minWidth, max = EditorTokens.Inspector.maxWidth)
            .clip(RoundedCornerShape(9.dp))
            .background(warn.copy(alpha = 0.14f))
            .border(1.dp, warn.copy(alpha = 0.32f), RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp)
            .testTag("editor-skipped-notice"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BasicText(
            text = if (count == 1) "1 operation was not applied" else "$count operations were not applied",
            style = TextStyle(color = warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            text = buildString {
                if (titles.isNotEmpty()) {
                    append(titles.joinToString(" and "))
                    append(if (titles.size == 1) " is" else " are")
                    append(" switched off in Settings. ")
                }
                append("Their edits are kept and will apply again once the tools are back.")
            },
            style = TextStyle(color = Theme.viewer.onScrimSecondary, fontSize = 11.5.sp)
        )
    }
}
