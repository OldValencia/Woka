package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import com.woka.data.MediaItem

@Composable
expect fun NativeMediaViewer(
    initialId: String,
    mediaItems: List<MediaItem>,
    onClose: (String) -> Unit
)
