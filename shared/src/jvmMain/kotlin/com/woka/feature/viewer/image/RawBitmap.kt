package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_BYTE

private const val RGB = 3
private const val RGBA = 4

/**
 * LibRaw's packed RGB, at [address], written straight into a Skia bitmap's own
 * pixel memory — row by row, through Java FFM.
 *
 * Not through [toStillBitmap]: its PNG round trip is fine for a 64-megapixel
 * HEIC tile grid's preview, but a full RAW is the one picture that goes through
 * it at native size, and on an 81-megapixel RW2 `ImageIO`'s file cache threw
 * `EOFException` mid-encode (2026-10-24). The encode and decode were also most
 * of the 3–12 s a full development took. Here the only copy is the one Skia
 * needs, off the Java heap; the heap holds one row at a time.
 */
internal fun rgbToBitmap(address: Long, width: Int, height: Int): ImageBitmap {
    val bitmap = Bitmap()
    check(bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE))) {
        "could not allocate a ${width}×$height bitmap"
    }
    val pixmap = bitmap.peekPixels() ?: error("bitmap has no pixels")
    val rowBytes = pixmap.rowBytes.toLong()
    val source = MemorySegment.ofAddress(address).reinterpret(width.toLong() * height * RGB)
    val target = MemorySegment.ofAddress(pixmap.addr).reinterpret(rowBytes * height)
    val rgb = ByteArray(width * RGB)
    val rgba = ByteArray(width * RGBA)
    for (y in 0 until height) {
        MemorySegment.copy(source, JAVA_BYTE, y.toLong() * rgb.size, rgb, 0, rgb.size)
        for (x in 0 until width) {
            rgba[x * RGBA] = rgb[x * RGB]
            rgba[x * RGBA + 1] = rgb[x * RGB + 1]
            rgba[x * RGBA + 2] = rgb[x * RGB + 2]
            rgba[x * RGBA + 3] = -1
        }
        MemorySegment.copy(rgba, 0, target, JAVA_BYTE, y * rowBytes, rgba.size)
    }
    bitmap.notifyPixelsChanged()
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}
