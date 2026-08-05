package com.woka.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap

/**
 * Decode cache shared by the viewer's still loader and the video poster-frame
 * extractor. Split out of `ThumbnailLoader` when that object was divided between
 * `viewer/image` and `viewer/video/engine`.
 *
 * There is deliberately **no eviction policy** here: that is what the original
 * had, and changing it is a review decision, not a restructure one. See
 * `viewer-verification-pass.md` § F1.
 */
object ImageBitmapCache {
    private val entries = ConcurrentHashMap<String, ImageBitmap>()

    /** Two decodes at a time, as the loader has always throttled them. */
    val ioDispatcher = Dispatchers.IO.limitedParallelism(2)

    operator fun get(key: String): ImageBitmap? = entries[key]

    operator fun set(key: String, bitmap: ImageBitmap) {
        entries[key] = bitmap
    }
}
