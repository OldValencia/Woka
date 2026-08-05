package com.woka.feature.viewer

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.feature.viewer.shell.MediaViewerScreen

@Composable
actual fun NativeMediaViewer(
    initialId: String,
    mediaItems: List<MediaItem>,
    onClose: (String) -> Unit
) {
    MediaViewerScreen(
        initialMediaId = initialId,
        mediaItems = mediaItems,
        onClose = onClose,
        loadThumbnail = { item -> MediaThumbnails.loadThumbnail(item) }
    )
}
