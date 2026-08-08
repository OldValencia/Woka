package com.woka.feature.viewer

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.feature.viewer.shell.MediaViewerScreen
import com.woka.feature.viewer.shell.ViewerThumbnails

/** The real decoders. Tests substitute their own — see `ViewerHarness`. */
private object RealViewerThumbnails : ViewerThumbnails {
    override suspend fun full(item: MediaItem, frameIndex: Int) =
        MediaThumbnails.loadThumbnail(item, frameIndex)
    override suspend fun preview(item: MediaItem, frameIndex: Int) =
        MediaThumbnails.loadPreview(item, frameIndex)
    override suspend fun sourceSize(item: MediaItem) = MediaThumbnails.sourceSize(item)
    override suspend fun stillColor(item: MediaItem) = MediaThumbnails.stillColor(item)
    override fun cachedFull(item: MediaItem, frameIndex: Int) =
        ImageBitmapCache[ImageBitmapCache.key(item.file.absolutePath, frameIndex)]
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
