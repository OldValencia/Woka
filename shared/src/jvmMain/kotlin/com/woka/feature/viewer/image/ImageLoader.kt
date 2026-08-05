package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.ui.image.ImageBitmapCache
import org.jetbrains.skia.Image
import java.io.File

/**
 * Decodes a still image at its native resolution for the viewer. The grid never
 * comes through here — it goes via Coil — so nothing downsamples on this path.
 */
object ImageLoader {
    fun load(file: File): ImageBitmap {
        val bitmap = Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        ImageBitmapCache[file.absolutePath] = bitmap
        return bitmap
    }
}
