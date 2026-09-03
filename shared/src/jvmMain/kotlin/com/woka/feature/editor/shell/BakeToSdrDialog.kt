package com.woka.feature.editor.shell

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.dialogs.ConfirmationDialog
import com.woka.core.ui.theme.Theme

/**
 * Asked before `To SDR`, and **the only question in this editor that is about the
 * photograph itself** (ME-118).
 *
 * Every other destructive control here endangers *work*: Revert throws away an
 * afternoon's edit but the pixels have never been touched, so the picture that came off
 * the camera is still in the file. `To SDR` is different in kind — it rewrites the
 * pixels, and the PQ codes it replaces are not recoverable from anything Woka keeps.
 * Revert afterwards finds an SDR photograph and returns it unchanged.
 *
 * So the body says what is lost rather than that something is, and it says *why anyone
 * would* — since ME-177 Woka draws these files correctly, and the only reason to bake is
 * an application that does not.
 */
@Composable
internal fun BakeToSdrDialog(onBake: () -> Unit, onKeepEditing: () -> Unit) {
    ConfirmationDialog(
        title = "Convert to SDR?",
        onConfirm = onBake,
        onDismiss = onKeepEditing,
        width = 380.dp,
        confirmText = "Convert",
        dismissText = "Keep HDR"
    ) {
        BasicText(
            text = BODY,
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 14.sp)
        )
    }
}

private const val BODY =
    "The tone-mapped picture is written into this file and its high dynamic range is " +
        "gone for good — Revert will not bring it back, because there will be nothing " +
        "left to revert to.\n\nWoka already shows this photograph correctly. Convert it " +
        "only for an app that does not."
