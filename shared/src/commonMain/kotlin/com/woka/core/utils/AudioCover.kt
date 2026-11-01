package com.woka.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/** A track's cover — embedded, or `cover.jpg` beside it — or null when it has none (AP-003). */
expect suspend fun loadAudioCover(file: File): ImageBitmap?
