package io.iris.utils

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

expect suspend fun generateVideoThumbnail(file: File): ImageBitmap?
