package io.iris.ui.viewer

import androidx.compose.runtime.Composable
import io.iris.data.MediaItem
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun NativeMediaViewer(
    initialId: String,
    mediaItems: List<MediaItem>,
    onClose: () -> Unit
) {
    MediaViewerScreen(
        initialMediaId = initialId,
        mediaItems = mediaItems,
        onClose = onClose,
        loadThumbnail = { item ->
            withContext(Dispatchers.IO) {
                try {
                    val bytes = item.file.readBytes()
                    Image.makeFromEncoded(bytes).toComposeImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        }
    )
}
