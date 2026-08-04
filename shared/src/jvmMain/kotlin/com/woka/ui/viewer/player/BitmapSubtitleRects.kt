package com.woka.ui.viewer.player

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avcodec.AVSubtitle
import org.bytedeco.ffmpeg.avcodec.AVSubtitleRect
import org.bytedeco.ffmpeg.global.avcodec.SUBTITLE_BITMAP
import org.bytedeco.javacpp.BytePointer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Image as SkiaImage

/**
 * Converts the PAL8 rectangles of a decoded PGS / VobSub subtitle into
 * [SubtitleImageRect]s (Step 10 / §2.2), using the same Skia raster path decoded
 * video frames take (`toComposeBitmap` in `VideoPlayerExt.kt`).
 */
internal fun AVSubtitle.toImageRects(): List<SubtitleImageRect> {
    val result = mutableListOf<SubtitleImageRect>()
    for (i in 0 until num_rects()) {
        val rect = rects(i) ?: continue
        if (rect.type() != SUBTITLE_BITMAP) continue
        rect.toImageRect()?.let { result += it }
    }
    return result
}

private fun AVSubtitleRect.toImageRect(): SubtitleImageRect? {
    val width = w()
    val height = h()
    val stride = linesize(0)
    if (width <= 0 || height <= 0 || stride < width) return null

    val pixels = data(0)?.readBytes(stride * height) ?: return null
    // FFmpeg always allocates AVPALETTE_SIZE for a PAL8 rect, but a decoder may
    // declare fewer entries; anything past the declared count reads as transparent.
    val colors = nb_colors().takeIf { it in 1..MAX_PALETTE_COLORS } ?: MAX_PALETTE_COLORS
    val palette = data(1)?.readBytes(colors * 4) ?: return null

    val argb = ByteArray(width * height * 4)
    for (y in 0 until height) {
        val row = y * stride
        val out = y * width * 4
        for (x in 0 until width) {
            val entry = (pixels[row + x].toInt() and 0xFF) * 4
            if (entry + 3 >= palette.size) continue
            // The palette is uint32 ARGB in host order, so on a little-endian JVM
            // — the only one this desktop-only build runs on — the bytes come out
            // as B, G, R, A and have to be re-ordered for Skia's RGBA_8888.
            argb[out + x * 4] = palette[entry + 2]
            argb[out + x * 4 + 1] = palette[entry + 1]
            argb[out + x * 4 + 2] = palette[entry]
            argb[out + x * 4 + 3] = palette[entry + 3]
        }
    }

    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val image = SkiaImage.makeRaster(info, argb, width * 4).toComposeImageBitmap()
    return SubtitleImageRect(bitmap = image, x = x(), y = y())
}

private const val MAX_PALETTE_COLORS = 256

/**
 * The packet's payload as plain bytes — what a bitmap track retains instead of the
 * decoded image (Step 10): compressed RLE is roughly a fiftieth of the raster.
 */
internal fun AVPacket.copyPayload(): ByteArray? {
    val length = size()
    if (length <= 0) return null
    return data()?.readBytes(length)
}

/**
 * JavaCPP pointers returned from struct getters carry no capacity, so it has to be
 * declared before the bytes can be read — reading without it throws.
 */
private fun BytePointer.readBytes(length: Int): ByteArray {
    val bytes = ByteArray(length)
    val reader = BytePointer(this)
    reader.capacity(length.toLong())
    reader.position(0)
    reader.get(bytes)
    return bytes
}
