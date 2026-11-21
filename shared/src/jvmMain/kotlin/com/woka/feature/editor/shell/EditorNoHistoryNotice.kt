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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.EditorNoticeStatus
import com.woka.feature.editor.recipe.carrierFor
import java.io.File

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
internal fun EditorNoHistoryNotice() = EditorFormatNotice(
    title = "This format keeps no edit history",
    body = "Done re-encodes the picture in place. There is nothing to revert to " +
        "afterwards, and editing it again starts from the re-encoded file.",
    tag = "editor-no-history-notice"
)

/**
 * A film's bucket B (ME-289): nowhere to keep the edit, so `Done` is the render — mockup
 * state 9's words, shown for the whole session for this notice's own reason.
 *
 * **A format that has a carrier gets its own words** (reported 2026-11-20): one `mp4` kept
 * its edit and the next refused, and "MP4 cannot keep an edit" read as a contradiction. The
 * refusal is about how that one file is laid out, so the notice says so.
 */
@Composable
internal fun EditorRenderOnlyNotice(file: File) {
    val extension = file.extension.uppercase()
    if (carrierFor(file) == null) {
        EditorFormatNotice(
            title = "$extension cannot keep an edit yet",
            body = "Done is unavailable for this format. Render writes a new file; the original is untouched.",
            tag = "editor-render-only-notice"
        )
    } else {
        EditorFormatNotice(
            title = "This $extension cannot keep an edit",
            body = "Woka keeps an edit at the end of the file, and this one has no room there: it was " +
                "saved in fragments, as a stream or in parts, or the recording was cut short. Done is " +
                "unavailable; Render writes a new file and the original is untouched.",
            tag = "editor-render-only-file-notice"
        )
    }
}

/**
 * A target the factory refuses (ME-289): the reason in the notice strip, then the close —
 * never an editor that opens and shuts in one frame with nothing said.
 */
@Composable
internal fun EditorRefusedEffect(path: String, onClose: () -> Unit) {
    LaunchedEffect(path) {
        EditorNoticeStatus.show("${File(path).extension.uppercase()} files cannot be edited.")
        onClose()
    }
}

@Composable
private fun EditorFormatNotice(title: String, body: String, tag: String) {
    val warn = Theme.colors.accentFolders
    Column(
        modifier = Modifier
            .widthIn(min = EditorTokens.Inspector.minWidth, max = EditorTokens.Inspector.maxWidth)
            .clip(RoundedCornerShape(9.dp))
            .background(warn.copy(alpha = 0.14f))
            .border(1.dp, warn.copy(alpha = 0.32f), RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BasicText(
            text = title,
            style = TextStyle(color = warn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            text = body,
            style = TextStyle(color = Theme.viewer.onScrimSecondary, fontSize = 11.5.sp)
        )
    }
}
