package com.woka.feature.viewer.video

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.dialogs.ConfirmationDialog
import com.woka.core.ui.theme.Theme

@Composable
fun VideoAlreadyOpenedDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialog(
        title = "Video Already Playing",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        width = 320.dp,
        confirmText = "Yes",
        dismissText = "No"
    ) {
        Text(
            text = "You are already watching a video in Picture-in-Picture mode. Close the current video and open the new one?",
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 14.sp)
        )
    }
}
