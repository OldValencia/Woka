package com.woka.feature.viewer.shell

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrameSet
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.image.StillPreview
import com.woka.feature.viewer.image.stillDecoderFor
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

    suspend fun loadThumbnail(item: MediaItem, frameIndex: Int = 0): ImageBitmap? {
        ImageBitmapCache[ImageBitmapCache.key(item.file.absolutePath, frameIndex)]?.let { return it }
        return withContext(ImageBitmapCache.ioDispatcher) {
            try {
                if (item is MediaItem.Video) {
                    VideoThumbnailExtractor.load(item)
                } else {
                    ImageLoader.load(item.file, frameIndex)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * A small copy, for the carousel — which loads **every file in the folder**
     * and draws each 56–72 dp wide.
     *
     * Videos already answer this cheaply: [VideoThumbnailExtractor] has always
     * returned a 400 px poster read from disk. Stills used to come back at
     * native resolution here, which was survivable while every still was a
     * JPEG and ruinous once one could be a 36-megapixel HEIC — see
     * [StillPreview].
     */
    suspend fun loadPreview(item: MediaItem, frameIndex: Int = 0): ImageBitmap? =
        withContext(ImageBitmapCache.ioDispatcher) {
            try {
                if (item is MediaItem.Video) {
                    VideoThumbnailExtractor.load(item)
                } else {
                    StillPreview.load(item.file, frameIndex)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /** A still's own dimensions, read from the decoder without decoding it. */
    suspend fun sourceSize(item: MediaItem): IntSize =
        withContext(ImageBitmapCache.ioDispatcher) {
            if (item is MediaItem.Video) {
                IntSize.Zero
            } else {
                runCatching { stillDecoderFor(item.file).nativeSize(item.file, 0) }
                    .getOrDefault(IntSize.Zero)
            }
        }

    /**
     * What is inside this file — a **separate** call from [loadThumbnail], which
     * keeps its signature and keeps returning the one primary bitmap every
     * existing caller wants. The grid and the carousel never ask this.
     *
     * Cheap by design: it counts frames without decoding any of them — skia
     * reads the codec's frame count, `ImageIO` scans for pages, and FFmpeg
     * reads the demuxer's stream table, so a five-shot HEIC burst answers
     * without a single pixel being decoded.
     *
     * A decoder that throws answers [ImageFrameSet.Single]: a file that cannot
     * be opened has no frame set to show, and the viewer's placeholder is
     * already the right thing on screen for it.
     */
    suspend fun frameSet(item: MediaItem): ImageFrameSet =
        withContext(ImageBitmapCache.ioDispatcher) {
            if (item is MediaItem.Video) {
                ImageFrameSet.Single
            } else {
                runCatching { stillDecoderFor(item.file).frameSet(item.file) }
                    .getOrDefault(ImageFrameSet.Single)
            }
        }
}
