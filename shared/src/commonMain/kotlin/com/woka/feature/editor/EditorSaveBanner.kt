package com.woka.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import com.woka.core.ui.components.TOAST_PLAIN_MS
import com.woka.core.ui.components.Toast
import com.woka.core.ui.components.ToastText
import com.woka.core.ui.components.rememberToastTimer

/**
 * What `Done` is doing, once the editor it was pressed in has closed (ME-130), in the
 * prototype's toast since RD-401 — "Edits kept. The original file is untouched." among them.
 *
 * What is this producer's own is the settle rule: a running save holds the toast up for as
 * long as the write takes, and only its answer takes itself away — after
 * [TOAST_PLAIN_MS], paused while the pointer is on it.
 */
@Composable
fun EditorSaveBanner() {
    val message = EditorSaveStatus.message
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        if (message == null) return@AnimatedVisibility
        val running = message.startsWith(SAVING_PREFIX)
        val onHover = rememberToastTimer(message, if (running) Long.MAX_VALUE else TOAST_PLAIN_MS) {
            if (!running) EditorSaveStatus.clear()
        }
        Toast("editor-save-banner", onHover = onHover) {
            ToastText(message)
        }
    }
}
