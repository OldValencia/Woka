package com.woka.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.MediaThumbnails
import java.io.File

actual suspend fun loadAudioCover(file: File): ImageBitmap? {
    return MediaThumbnails.loadThumbnail(MediaItem.Audio(file))
}
