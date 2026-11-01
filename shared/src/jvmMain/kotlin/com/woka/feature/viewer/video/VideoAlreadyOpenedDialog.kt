package com.woka.feature.viewer.video

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.dialogs.ConfirmationDialog
import com.woka.core.ui.theme.Theme

@Composable
fun VideoAlreadyOpenedDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** The thing playing is a track, not a video in PiP (AP-003). */
    music: Boolean = false,
    /** What is being opened is a track: the PiP film would be closed for music (AP-003). */
    toMusic: Boolean = false
) {
    ConfirmationDialog(
        title = if (music) "Music Is Playing" else "Video Already Playing",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        width = 320.dp,
        confirmText = "Yes",
        dismissText = "No"
    ) {
        BasicText(
            text = if (music) {
                "Music is playing. Stop it and open the video?"
            } else if (toMusic) {
                "A video is playing in Picture-in-Picture mode. Close it and play this track?"
            } else {
                "You are already watching a video in Picture-in-Picture mode. Close the current video and open the new one?"
            },
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 14.sp)
        )
    }
}
