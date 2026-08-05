package com.woka.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.MediaThumbnails
import java.io.File

actual suspend fun generateVideoThumbnail(file: File): ImageBitmap? {
    return MediaThumbnails.loadThumbnail(MediaItem.Video(file))
}
