package io.iris.ui.viewer

import androidx.compose.runtime.Composable
import io.iris.data.MediaItem

@Composable
expect fun NativeMediaViewer(
    initialId: String,
    mediaItems: List<MediaItem>,
    onClose: (String) -> Unit
)
