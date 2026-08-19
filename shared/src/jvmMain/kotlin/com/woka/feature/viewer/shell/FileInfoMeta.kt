package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.rememberReloadKey
import com.woka.core.ui.theme.Theme
import java.io.File
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
            NameLine(item.file)
            InfoLine("Path: ${item.file.absolutePath}")
            InfoLine("Size: ${item.file.length().toReadableSize()}")
            InfoLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(item.file.lastModified()))}")
        }
    }
}

/**
 * The name, and whether this photograph is edited.
 *
 * **Two sources, because there are two ways to be edited.** The shell's override says an
 * editor is open over unsaved work — a fact about the app, not about the `MediaItem`.
 * The file itself says whether it carries a recipe, and that is the one the *viewer*
 * needs: outside the editor nothing sets the override, so an edited photograph read as
 * an untouched one (found by hand, 2026-08-19).
 *
 * Keyed on the reload signal, so pressing `Done` behind this dialog moves the mark
 * (ME-135) instead of leaving the answer taken when it opened.
 */
@Composable
private fun NameLine(file: File) {
    var carries by remember(file.absolutePath) { mutableStateOf(false) }
    val reloadKey = rememberReloadKey(file.absolutePath)
    LaunchedEffect(file.absolutePath, reloadKey) {
        carries = AppliedStillEdits.carries(file)
    }
    val edited = LocalCanvasOverride.current.edited || carries
    val name = file.name
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
