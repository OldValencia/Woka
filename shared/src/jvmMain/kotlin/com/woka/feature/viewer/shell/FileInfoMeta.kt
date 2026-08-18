package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.app.shell.LocalCanvasOverride
import com.woka.core.data.MediaItem
import com.woka.core.ui.theme.Theme
import java.text.SimpleDateFormat
import java.util.Date

/** The four lines every file has, video or not. Split out of `FileInfoDialog` on size. */
@Composable
internal fun FileMetaBlock(item: MediaItem) {
    SelectionContainer(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NameLine(item.file.name)
            InfoLine("Path: ${item.file.absolutePath}")
            InfoLine("Size: ${item.file.length().toReadableSize()}")
            InfoLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(item.file.lastModified()))}")
        }
    }
}

/**
 * The name, and whether an editor is holding changes the file does not have yet.
 *
 * Read from the shell rather than passed in: "there is an unsaved edit" is a fact about
 * the app, not about the `MediaItem`, and this dialog opens from the pill the editor has
 * already taken over. It replaced a `· Edited` in the top bar, which went when the file
 * name there gave its place back to that pill (§4.4, 2026-08-18).
 */
@Composable
private fun NameLine(name: String) {
    val edited = LocalCanvasOverride.current.edited
    // One text, not a `Row` of two: beside a name that already wraps, the mark was
    // handed whatever width was left over and broke to one letter per line.
    val text = buildAnnotatedString {
        append("Name: $name")
        if (edited) {
            withStyle(SpanStyle(color = Theme.colors.accent)) {
                append("  • Edited")
            }
        }
    }
    BasicText(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (edited) Modifier.testTag(EDITED_TAG) else Modifier),
        style = TextStyle(
            color = if (edited) Theme.colors.textPrimary else Theme.colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (edited) FontWeight.SemiBold else FontWeight.Normal
        )
    )
}

internal const val EDITED_TAG = "editor-dirty"

@Composable
private fun InfoLine(text: String) {
    BasicText(
        text,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(color = Theme.colors.textSecondary, fontSize = 13.sp)
    )
}
