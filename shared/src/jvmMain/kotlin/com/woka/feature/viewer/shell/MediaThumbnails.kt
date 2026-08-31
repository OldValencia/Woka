package com.woka.feature.viewer.shell

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrameSet
import com.woka.core.data.StillColorInfo
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.image.StillPreview
import com.woka.feature.viewer.image.readStillColor
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.feature.viewer.video.engine.VideoThumbnailExtractor
import com.woka.feature.viewer.video.engine.quietFfmpegLogs
import kotlinx.coroutines.CancellationException
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
            decoded(fallback = null, report = true) {
                if (item is MediaItem.Video) {
                    VideoThumbnailExtractor.load(item)
                } else {
                    ImageLoader.load(item.file, frameIndex)
                }
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
            decoded(fallback = null, report = true) {
                if (item is MediaItem.Video) {
                    VideoThumbnailExtractor.load(item)
                } else {
                    StillPreview.load(item.file, frameIndex)
                }
            }
        }

    /** A still's own dimensions, read from the decoder without decoding it. */
    suspend fun sourceSize(item: MediaItem): IntSize =
        withContext(ImageBitmapCache.ioDispatcher) {
            if (item is MediaItem.Video) {
                IntSize.Zero
            } else {
                // Quiet: a file this fails on is one the loader is about to fail on
                // too, and print about. One report per broken file is enough.
                decoded(fallback = IntSize.Zero, report = false) {
                    stillDecoderFor(item.file).nativeSize(item.file, 0)
                }
            }
        }

    /**
     * A still's transfer function and gamut. Read from FFmpeg for every format
     * — see [readStillColor] for why it does not follow the pixel path.
     */
    suspend fun stillColor(item: MediaItem): StillColorInfo =
        withContext(ImageBitmapCache.ioDispatcher) {
            if (item is MediaItem.Video) StillColorInfo.Sdr else readStillColor(item.file)
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
                decoded(fallback = ImageFrameSet.Single, report = false) {
                    stillDecoderFor(item.file).frameSet(item.file)
                }
            }
        }
}

/**
 * [body]'s answer, or [fallback] when it fails — the one place this facade decides what a
 * failed decode looks like.
 *
 * **Two things it does that the code it replaced did not.**
 *
 * `runCatching`, which `sourceSize` and `frameSet` used, catches `Throwable`. That is
 * *wider* than `Exception`, not narrower, so an `OutOfMemoryError` out of a decoder handed
 * back a plausible `IntSize.Zero` the caller could not tell from a real answer. An `Error`
 * now travels.
 *
 * And cancellation is rethrown before anything else is caught, which is the idiom the video
 * engine already uses (`VideoPlayerDecoder`). **Today this cannot fire**: nothing inside any
 * of these blocks suspends — `ImageLoader.load`, `StillPreview.load`,
 * `VideoThumbnailExtractor.load` and the `StillDecoder` surface are all plain blocking
 * functions — so `withContext` only ever throws `CancellationException` at its own boundary,
 * outside these bodies. It is here because the day one of those decoders gains a `suspend`,
 * a swallowed cancellation would appear silently, and a caller would read "you gave up" as
 * "this file has no picture".
 *
 * [report] is false where a failure is an ordinary answer rather than news.
 */
private inline fun <T> decoded(fallback: T, report: Boolean, body: () -> T): T =
    try {
        body()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (report) e.printStackTrace()
        fallback
    }
