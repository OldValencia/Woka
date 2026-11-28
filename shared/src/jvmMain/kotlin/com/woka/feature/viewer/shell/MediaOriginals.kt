package com.woka.feature.viewer.shell

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.ImageLoader
import kotlinx.coroutines.withContext

/**
 * A still's frame as shot, for the viewer's `Original` (RD-502), under [MediaThumbnails]'
 * failure policy ([decoded]). Beside it rather than in it because that object is at the
 * size mandate.
 *
 * **Stills only.** A film compares by drawing its frame without the edit's painter
 * (`LocalViewerOriginal`), and a track has no picture to compare; both answer null, which
 * keeps whatever is on screen.
 */
internal suspend fun loadOriginalStill(item: MediaItem, frameIndex: Int): ImageBitmap? {
    if (item !is MediaItem.Image) return null
    return withContext(ImageBitmapCache.ioDispatcher) {
        decoded(fallback = null, report = true) { ImageLoader.loadOriginal(item.file, frameIndex) }
    }
}
