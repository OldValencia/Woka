package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import com.woka.data.MediaItem

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
        loadThumbnail = { item -> ThumbnailLoader.loadThumbnail(item) }
    )
}
