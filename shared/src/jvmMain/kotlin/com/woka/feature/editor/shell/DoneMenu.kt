package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.woka.core.ui.components.MenuSurface
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorExport

/** `video-editor.html` state 9's menu is wider than a context menu: each entry has a line of cost. */
private val MENU_WIDTH = 260.dp

/**
 * What `Done ▾` offers beside `Done`, and what choosing one does (`video-editor.md` §4.5).
 *
 * **A composition local, because the bar is drawn somewhere else.** `EditorTopBarSetup`
 * hands `GlobalTopBar` a lambda, and that lambda composes under the bar, not under the
 * editor — so a value provided around the editor would not reach it. The setup reads this
 * under the editor and carries it across with `rememberUpdatedState`, as it carries
 * everything else.
 */
@Immutable
internal class DoneChoices(
    val exports: List<EditorExport>,
    val onExport: (EditorExport) -> Unit,
    /** False for `RenderOnly` (ME-289): the file cannot keep the edit, so `Done` gives way. */
    val keeps: Boolean = true
) {
    /** What the button does in `Done`'s place — the first export — or null where `Done` stays. */
    val instead: (() -> Unit)?
        get() = exports.firstOrNull()?.takeIf { !keeps }?.let { export -> { onExport(export) } }
}

internal fun doneChoicesOf(engine: EditorEngine, onExport: (EditorExport) -> Unit) =
    DoneChoices(engine.exports, onExport, keeps = engine.capability != EditCapability.RenderOnly)

internal val LocalDoneChoices = staticCompositionLocalOf { DoneChoices(emptyList(), {}) }

/**
 * The menu under the caret: `Done`, a divider, then each export — every entry with the
 * line that says what it costs, in the mockup's words.
 */
@Composable
internal fun DoneMenu(choices: DoneChoices, onDone: () -> Unit, onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 40),
        onDismissRequest = onDismiss
    ) {
        MenuSurface(Modifier.width(MENU_WIDTH).testTag("editor-done-menu")) {
            DoneMenuEntry(
                title = "Done",
                summary = "Instant. The edit goes into the file, the video is not re-encoded.",
                tag = "editor-done-menu-done"
            ) {
                onDismiss()
                onDone()
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Theme.colors.border))
            choices.exports.forEach { export ->
                DoneMenuEntry(export.title, export.summary, "editor-export-${export.id}") {
                    onDismiss()
                    choices.onExport(export)
                }
            }
        }
    }
}

@Composable
private fun DoneMenuEntry(title: String, summary: String, tag: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(tag)
    ) {
        BasicText(
            text = title,
            style = TextStyle(color = Theme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        )
        BasicText(
            text = summary,
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 11.5.sp)
        )
    }
}
