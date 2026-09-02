package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import java.io.File

/**
 * Decodes a still image at its native resolution for the viewer. The grid never
 * comes through here — it goes via Coil — so nothing downsamples on this path.
 */
object ImageLoader {
    /**
     * Frame [frameIndex] of [file], cached and returned.
     *
     * Which library does the work is [stillDecoderFor]'s decision and nothing
     * else's; this only picks the cache key. Zero is the primary frame and the
     * whole of the pre-frame-set behaviour, cached under the bare path.
     *
     * It **throws** on a file it cannot read — a corrupt image, a missing one, a
     * format with no decoder behind its extension. `MediaThumbnails` is where
     * every call goes and where that becomes a placeholder. This used to answer
     * null for `.heic` before asking, which is no longer true of any listed
     * format: everything the app lists now has a decoder routed to it.
     */
    fun load(file: File, frameIndex: Int = 0): ImageBitmap {
        // **Tone mapped before the edit, not after** (ME-177). The recipe is folded
        // over displayable colour, so an Adjust value means the same thing here as it
        // does on the editor's canvas.
        val decoded = displayable(file, stillDecoderFor(file).frame(file, frameIndex))
        // **Before caching, not after.** The cache holds what the viewer draws, so an
        // entry made from the raw decode would serve the unedited picture to every
        // reader that hits it — and the edit would appear only on a cold miss (ME-133).
        val bitmap = AppliedStillEdits.apply(file, frameIndex, decoded)
        ImageBitmapCache[ImageBitmapCache.key(file.absolutePath, frameIndex)] = bitmap
        return bitmap
    }
}
