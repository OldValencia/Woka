package com.woka.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import com.woka.core.ui.components.TOAST_DONE_MS
import com.woka.core.ui.components.TOAST_PLAIN_MS
import com.woka.core.ui.components.Toast
import com.woka.core.ui.components.ToastAction
import com.woka.core.ui.components.ToastProgress
import com.woka.core.ui.components.ToastText
import com.woka.core.ui.components.rememberToastTimer
import com.woka.core.utils.openInFileManager
import java.io.File

/**
 * A job's toast (RD-401, `refine.js` `startJob`): "<label>…", the bar and `Cancel` while it
 * runs; then "Saved next to the original." with `Show in folder`, or the reason it stopped.
 *
 * It replaced ME-293's banner (name, bar, percent, ✕) in the prototype's form, still driven by
 * [EditorRenderStatus] — a render outlives the editor that started it.
 */
@Composable
fun EditorRenderBanner() {
    val message = EditorRenderStatus.message
    val fraction = EditorRenderStatus.fraction
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        if (message == null) return@AnimatedVisibility
        if (fraction != null) {
            Toast("editor-render-banner") {
                ToastText(message)
                ToastProgress(fraction)
                ToastAction("Cancel", "editor-render-cancel") { EditorRenderStatus.cancel() }
            }
        } else {
            FinishedToast(message, EditorRenderStatus.savedTo)
        }
    }
}

/** The answer: a saved file can be shown in its folder, and stays up a little longer for it. */
@Composable
private fun FinishedToast(message: String, savedTo: String?) {
    val onHover = rememberToastTimer(message, if (savedTo != null) TOAST_DONE_MS else TOAST_PLAIN_MS) {
        EditorRenderStatus.clear()
    }
    Toast("editor-render-banner", onHover = onHover) {
        ToastText(message)
        if (savedTo != null) {
            ToastAction("Show in folder", "editor-render-show") {
                File(savedTo).absoluteFile.parentFile?.let { openInFileManager(it.path) }
            }
        }
    }
}
