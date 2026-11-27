package com.woka.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.core.ui.components.TOAST_UNDO_MS
import com.woka.core.ui.components.Toast
import com.woka.core.ui.components.ToastAction
import com.woka.core.ui.components.ToastText
import com.woka.core.ui.components.rememberToastTimer

/** A removal that can still be taken back: what it says, and the session's undo. */
class PendingUndo(val message: String, val undo: () -> Unit)

/**
 * The last removal in the editor, for as long as its toast is up (RD-401, `app.js`
 * `toast(message, true)`): "Clip removed. The remaining clips close the gap.", "Text removed."
 *
 * `UnlinkUndo`'s shape. **`Undo` is the session's own undo** — one step back, the step the
 * removal made — so it is exactly the toolbar's Undo, offered where the eye already is.
 *
 * [dismiss] runs when the editor closes: an undo handed to a session that is gone would
 * move nothing anyone can see.
 */
@Stable
object EditorUndoToast {
    var pending by mutableStateOf<PendingUndo?>(null)
        private set

    fun offer(message: String, undo: () -> Unit) {
        pending = PendingUndo(message, undo)
    }

    fun undo() {
        val last = pending ?: return
        pending = null
        last.undo()
    }

    fun dismiss(of: PendingUndo? = pending) {
        if (pending === of) pending = null
    }
}

/** The toast itself: the sentence and a quiet `Undo`, for [TOAST_UNDO_MS], paused on hover. */
@Composable
fun EditorUndoBanner() {
    val last = EditorUndoToast.pending ?: return
    val onHover = rememberToastTimer(last, TOAST_UNDO_MS) { EditorUndoToast.dismiss(last) }
    Toast("editor-undo-toast", onHover = onHover) {
        ToastText(last.message)
        ToastAction("Undo", "editor-undo-toast-undo") { EditorUndoToast.undo() }
    }
}
