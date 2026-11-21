package com.woka.feature.editor

import com.woka.core.data.MediaItem
import com.woka.core.ui.image.AppliedVideoEdits
import com.woka.core.utils.copyFilesToClipboard
import com.woka.core.utils.copyImageToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The grid and viewer share the same copy action and edited-video explanation. */
suspend fun copyMediaToClipboard(item: MediaItem, frameIndex: Int = 0) {
    if (item is MediaItem.Image) {
        if (!copyImageToClipboard(item.file.absolutePath, frameIndex)) {
            EditorNoticeStatus.show("The photo could not be copied. Try Render photo…")
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
