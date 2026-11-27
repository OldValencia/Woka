package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme

/**
 * `Save frame as photo` (RD-401, `app.js` `outputDialog('frame')`): the frame under the play
 * head, [atMs], as a PNG beside the film. One card — what, when — and `Save`.
 */
@Composable
internal fun FramePhotoDialog(atMs: Long, onSave: () -> Unit, onCancel: () -> Unit) {
    val room = Theme.room
    EditorDialogFrame("Save frame as photo", "editor-frame-dialog", onDismiss = onCancel) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .clip(RoundedCornerShape(EditorDialogTokens.Help.radius))
                .background(room.well)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    "Current frame · PNG",
                    style = TextStyle(color = room.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )
                BasicText(clockOf(atMs), style = TextStyle(color = room.muted, fontSize = 12.sp))
            }
            EditorDialogButton("Save", "editor-frame-save", DialogButtonKind.Primary, onSave)
        }
    }
}

/** `1:05` — the moment the frame is taken from. */
private fun clockOf(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
