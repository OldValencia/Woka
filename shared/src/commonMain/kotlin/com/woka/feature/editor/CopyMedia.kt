package com.woka.feature.editor

import com.woka.core.data.MediaItem
import com.woka.core.ui.image.AppliedVideoEdits
import com.woka.core.ui.image.copiesAsFileOnly
import com.woka.core.utils.copyFilesToClipboard
import com.woka.core.utils.copyImageToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where a copy runs: the app's lifetime, not the caller's. The grid's menu launched it in
 * its own scope, and closing the menu — the first thing a click on Copy does — cancelled
 * a GIF's copy before it reached the clipboard; Esc right after Ctrl+C did the same in the
 * viewer.
 */
private val copyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** The grid and viewer share the same copy action and edited-video explanation. */
fun copyMedia(item: MediaItem, frameIndex: Int = 0) {
    copyScope.launch { copyMediaToClipboard(item, frameIndex) }
}

private suspend fun copyMediaToClipboard(item: MediaItem, frameIndex: Int) {
    if (item is MediaItem.Image) {
        if (!copyImageToClipboard(item.file.absolutePath, frameIndex)) {
            EditorNoticeStatus.show(failureFor(item))
        }
        return
    }
    val editedVideo = item is MediaItem.Video && withContext(Dispatchers.IO) {
        AppliedVideoEdits.editOf(item.file) != null
    }
    copyFilesToClipboard(listOf(item.file.absolutePath))
    if (editedVideo) {
        EditorNoticeStatus.show("Original video copied. Use Render video… to include your edits.")
    }
}

private fun failureFor(item: MediaItem.Image): String =
    if (item.file.copiesAsFileOnly) "The GIF could not be copied."
    else "The photo could not be copied. Try Render photo…"
