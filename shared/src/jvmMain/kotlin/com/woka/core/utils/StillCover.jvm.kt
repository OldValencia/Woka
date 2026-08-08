package com.woka.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.MediaThumbnails
import java.io.File

actual suspend fun loadStillCover(file: File): ImageBitmap? =
    MediaThumbnails.loadPreview(MediaItem.Image(file))

actual suspend fun countFrames(file: File): Int =
    MediaThumbnails.frameSet(MediaItem.Image(file)).frames.size.coerceAtLeast(1)
