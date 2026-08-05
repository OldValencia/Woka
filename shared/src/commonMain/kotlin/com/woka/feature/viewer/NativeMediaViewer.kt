package com.woka.feature.viewer

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem

@Composable
expect fun NativeMediaViewer(
    initialId: String,
    mediaItems: List<MediaItem>,
    onClose: (String) -> Unit
)
