package io.iris.utils

import androidx.compose.ui.graphics.ImageBitmap
import io.iris.data.MediaItem
import io.iris.ui.viewer.ThumbnailLoader
import java.io.File

actual suspend fun generateVideoThumbnail(file: File): ImageBitmap? {
    return ThumbnailLoader.loadThumbnail(MediaItem.Video(file))
}
