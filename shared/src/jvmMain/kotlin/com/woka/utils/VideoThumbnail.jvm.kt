package com.woka.utils

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.data.MediaItem
import com.woka.ui.viewer.ThumbnailLoader
import java.io.File

actual suspend fun generateVideoThumbnail(file: File): ImageBitmap? {
    return ThumbnailLoader.loadThumbnail(MediaItem.Video(file))
}
