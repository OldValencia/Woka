package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.woka.core.ui.theme.Theme

/**
 * The prototype's dialog over the editor (RD-401, `editor.css` `dialog`): the room's panel,
 * a hairline, a title with `×` beside it, then the body and the actions at the lower right.
 * [compact] is `.dialog-compact` — `Save your changes?` and `Create a finished copy` — and
 * the rest take the full frame: `Keep your changes?`, `Revert to original?`, the help sheet.
 *
 * **`Theme.room`**: these open over the editor only, so they take its palette, never the
 * app's. The window's own dialog supplies the 60 % backdrop and the rise-in.
 */
@Composable
internal fun EditorDialogFrame(
    title: String,
    tag: String,
    onDismiss: () -> Unit,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val frame = EditorDialogTokens.Frame
    val small = EditorDialogTokens.Compact
    val shape = RoundedCornerShape(if (compact) small.radius else frame.radius)
    val room = Theme.room
    // The editor's keys stand aside while this is up — see `CanvasKeys.dialogOpen`.
    val keys = LocalCanvasKeys.current
    DisposableEffect(keys) {
        keys?.dialogOpen = true
        onDispose { keys?.dialogOpen = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .widthIn(max = if (compact) small.maxWidth else frame.maxWidth)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .shadow(24.dp, shape)
                .background(room.panel, shape)
                .border(1.dp, room.line, shape)
                .padding(if (compact) small.padding else frame.padding)
                .testTag(tag)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = if (compact) small.titleGap else frame.titleGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        color = room.text,
                        fontSize = if (compact) small.titleSize else frame.titleSize,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                EditorDialogButton("×", "$tag-close", DialogButtonKind.Quiet, onDismiss)
            }
            content()
        }
    }
}

/** `dialog p`: the room's muted tone, 14 at 1.7 — 13 at 1.45 in a compact dialog. */
@Composable
internal fun EditorDialogText(text: String, compact: Boolean = false, modifier: Modifier = Modifier) {
    val frame = EditorDialogTokens.Frame
    val small = EditorDialogTokens.Compact
    BasicText(
        text = text,
        modifier = modifier.padding(bottom = if (compact) 10.dp else 0.dp),
        style = TextStyle(
            color = Theme.room.muted,
            fontSize = if (compact) small.bodySize else frame.bodySize,
            lineHeight = if (compact) small.bodyLine else frame.bodyLine
        )
    )
}

/** `.dialog-actions`: right-aligned; [leading] stands alone at the left (`.dialog-leading-action`). */
@Composable
internal fun EditorDialogActions(
    compact: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val frame = EditorDialogTokens.Frame
    val small = EditorDialogTokens.Compact
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) small.actionsTop else frame.actionsTop),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) small.actionsGap else frame.actionsGap)
    ) {
        leading?.invoke()
        Spacer(Modifier.weight(1f))
        content()
    }
}
