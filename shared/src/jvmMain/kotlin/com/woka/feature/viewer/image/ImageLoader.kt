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
        val bitmap = Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        ImageBitmapCache[file.absolutePath] = bitmap
        return bitmap
    }
}
