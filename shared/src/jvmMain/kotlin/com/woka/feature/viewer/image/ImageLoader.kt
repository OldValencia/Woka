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
    /** Null for a format Skia cannot read (§2, HEIF) — the viewer shows a placeholder. */
    fun load(file: File): ImageBitmap? {
        if (!file.canDecode) return null
        // The `readBytes()` copy is unavoidable here: skiko 0.144.6 exposes only
        // `makeFromEncoded(ByteArray)` — no `Data` overload, no
        // `makeDeferredFromEncodedData` — so the encoded file is materialised on
        // the heap whatever the caller does (§10.7).
        // EXIF orientation needs nothing here: skiko's decoder applies the tag
        // itself, so a photo shot in portrait already comes back in portrait (F3,
        // measured rather than assumed — `ImageLoaderTest` guards it).
        val bitmap = Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        ImageBitmapCache[file.absolutePath] = bitmap
        return bitmap
    }
}
