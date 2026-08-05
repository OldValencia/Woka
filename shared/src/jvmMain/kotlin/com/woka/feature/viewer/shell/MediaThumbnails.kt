package com.woka.feature.viewer.shell

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.video.engine.VideoThumbnailExtractor
import com.woka.feature.viewer.video.engine.quietFfmpegLogs
import kotlinx.coroutines.withContext

/**
 * The viewer's single entry point for "give me a bitmap for this item". Replaces
 * `ThumbnailLoader`, which had grown to hold three unrelated things: the cache,
 * still decoding, and JavaCV poster-frame extraction.
 *
 * Despite the name it is not only thumbnails — stills come back at native
 * resolution, because the viewer draws this same bitmap full size.
 */
object MediaThumbnails {

    init {
        quietFfmpegLogs()
    }

    suspend fun loadThumbnail(item: MediaItem): ImageBitmap? {
        ImageBitmapCache[item.file.absolutePath]?.let { return it }
        return withContext(ImageBitmapCache.ioDispatcher) {
            try {
                if (item is MediaItem.Video) {
                    VideoThumbnailExtractor.load(item)
                } else {
                    ImageLoader.load(item.file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
