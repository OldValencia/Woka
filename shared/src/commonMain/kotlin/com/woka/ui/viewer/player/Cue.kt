package com.woka.ui.viewer.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class Cue(
    val startMs: Long,
    val endMs: Long,
    val text: String? = null,
    val bitmap: ImageBitmap? = null
)
