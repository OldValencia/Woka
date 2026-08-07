package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.data.canDecode
import com.woka.core.ui.image.ImageBitmapCache
import org.jetbrains.skia.Image
import java.io.File

/**
 * Decodes a still image at its native resolution for the viewer. The grid never
 * comes through here — it goes via Coil — so nothing downsamples on this path.
 */
object ImageLoader {
    /**
     * Null for a format Skia cannot read (§2, HEIF) — the viewer shows a
     * placeholder.
     *
     * [frameIndex] selects one frame of a frame set. **Zero is the whole of the
     * existing behaviour**, byte for byte: it still goes through
     * `Image.makeFromEncoded` and still caches under the bare path, so the
     * viewer, the carousel and `ImageLoaderTest` are untouched. Only a caller
     * that asks for a later frame reaches [SkiaFrameDecoder].
     */
    fun load(file: File, frameIndex: Int = 0): ImageBitmap? {
        if (!file.canDecode) return null
        val bitmap = if (frameIndex == 0) decodePrimary(file) else {
            SkiaFrameDecoder.frame(file, frameIndex)
        }
        ImageBitmapCache[ImageBitmapCache.key(file.absolutePath, frameIndex)] = bitmap
        return bitmap
    }

    // The `readBytes()` copy is unavoidable here: skiko 0.144.6 exposes only
    // `makeFromEncoded(ByteArray)` — no `Data` overload, no
    // `makeDeferredFromEncodedData` — so the encoded file is materialised on
    // the heap whatever the caller does (§10.7).
    // EXIF orientation needs nothing here: skiko's decoder applies the tag
    // itself, so a photo shot in portrait already comes back in portrait (F3,
    // measured rather than assumed — `ImageLoaderTest` guards it).
    private fun decodePrimary(file: File): ImageBitmap =
        Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
}
