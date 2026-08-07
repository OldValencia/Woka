package com.woka.feature.viewer.shell

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.ImageFrameSet
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.image.SkiaFrameDecoder
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

    /**
     * What is inside this file — a **separate** call from [loadThumbnail], which
     * keeps its signature and keeps returning the one primary bitmap every
     * existing caller wants. The grid and the carousel never ask this.
     *
     * Cheap by design: it counts frames without decoding any of them.
     *
     * Skia answers for every extension the app currently lists, so this is
     * truthful today rather than a placeholder — `.heic` is still gated by
     * `canDecode` and `.tif` is not listed at all. Phase 2b replaces the direct
     * call with the `StillDecoders` routing, at which point those two arrive.
     */
    suspend fun frameSet(item: MediaItem): ImageFrameSet =
        withContext(ImageBitmapCache.ioDispatcher) {
            if (item is MediaItem.Video) {
                ImageFrameSet.Single
            } else {
                runCatching { SkiaFrameDecoder.frameSet(item.file) }
                    .getOrDefault(ImageFrameSet.Single)
            }
        }
}
