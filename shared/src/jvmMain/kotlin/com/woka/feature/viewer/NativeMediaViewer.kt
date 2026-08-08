package com.woka.feature.viewer

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.feature.viewer.shell.MediaViewerScreen
import com.woka.feature.viewer.shell.ViewerThumbnails

/** The real decoders. Tests substitute their own — see `ViewerHarness`. */
private object RealViewerThumbnails : ViewerThumbnails {
    override suspend fun full(item: MediaItem) = MediaThumbnails.loadThumbnail(item)
    override suspend fun preview(item: MediaItem) = MediaThumbnails.loadPreview(item)
    override suspend fun sourceSize(item: MediaItem) = MediaThumbnails.sourceSize(item)
    override fun cachedFull(item: MediaItem) = ImageBitmapCache[item.file.absolutePath]
}

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
        thumbnails = RealViewerThumbnails
    )
}
