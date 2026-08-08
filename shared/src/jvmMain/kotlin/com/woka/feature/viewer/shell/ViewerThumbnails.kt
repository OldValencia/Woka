package com.woka.feature.viewer.shell

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaItem
import com.woka.core.data.StillColorInfo

/**
 * The two different pictures the viewer needs of one file, kept apart.
 *
 * They used to be one injected lambda, and the carousel — which loads **every
 * file in the folder** to draw each 56–72 dp wide — was therefore served
 * native-resolution decodes. With four tiled HEICs in a folder that is 264 MB
 * of pixels against a 256 MB cache: the two populations evict each other, every
 * scroll re-runs a tile assembly, and each picture is a 36-megapixel texture
 * uploaded to the GPU to be drawn thumbnail-sized.
 *
 * One interface rather than a second parameter because [MediaViewerScreen] and
 * [MediaViewerPlayer] are both at the file-size ceiling and this threads through
 * four files; it is also the honest shape, since the two are always supplied
 * together.
 */
interface ViewerThumbnails {

    /** Native resolution, for the picture the viewer actually draws. */
    suspend fun full(item: MediaItem, frameIndex: Int = 0): ImageBitmap?

    /** Small and disk-cached, for the carousel strip and the first paint. */
    suspend fun preview(item: MediaItem, frameIndex: Int = 0): ImageBitmap?

    /**
     * The file's own dimensions, without decoding it — what the zoom presets
     * mean by 100%, and what keeps them honest while [preview] is on screen.
     * [IntSize.Zero] when it cannot be read, which falls back to the poster.
     */
    suspend fun sourceSize(item: MediaItem): IntSize

    /**
     * The still's transfer function and gamut, so the frame can be drawn
     * through the right colour path. SDR for a video, whose frames carry their
     * own per-frame description on the player.
     */
    suspend fun stillColor(item: MediaItem): StillColorInfo

    /**
     * [full] if it is already decoded and in memory, else null — without
     * blocking or decoding.
     *
     * Exists so returning to a file already in the cache does not flash the
     * preview on the way: it would be a frame of blur bought for nothing.
     */
    fun cachedFull(item: MediaItem, frameIndex: Int = 0): ImageBitmap?
}
