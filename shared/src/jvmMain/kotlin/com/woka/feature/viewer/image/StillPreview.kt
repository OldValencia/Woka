package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.data.WokaStorage
import com.woka.core.data.markUsed
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.ui.image.encodeWebp
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest edge of a preview, matching the video poster cache's own 400 px. */
private const val PREVIEW_MAX_PX = 400

/**
 * A small, disk-cached copy of a still — what the carousel wants, as opposed to
 * the native-resolution bitmap the viewer draws.
 *
 * The carousel loads **every file in the folder** and draws each at 56–72 dp.
 * Serving those from the full decode was the reason opening a folder of HEICs
 * locked the UI up: four pictures at native resolution is 264 MB of decoded
 * pixels against a 256 MB cache, so the two populations evicted each other and
 * every scroll re-ran a tile assembly — and each one is a 36-megapixel texture
 * uploaded to the GPU to be drawn 72 dp wide.
 *
 * Videos have had exactly this treatment since the poster cache was written;
 * stills simply never needed it while they were all JPEGs. The disk cache is
 * keyed on path, size and mtime by [WokaStorage.thumbnailFile], so a preview
 * survives restarts and a file edited in place gets a new one.
 */
internal object StillPreview {

    fun load(file: File, frameIndex: Int = 0): ImageBitmap {
        val key = ImageBitmapCache.previewKey(file.absolutePath, frameIndex)
        ImageBitmapCache[key]?.let { return it }
        val cached = WokaStorage.thumbnailFile(file, frameIndex)
        val bitmap = readCached(cached) ?: build(file, cached, frameIndex)
        ImageBitmapCache[key] = bitmap
        return bitmap
    }

    private fun readCached(cached: File): ImageBitmap? {
        if (!cached.isFile) return null
        val bitmap = runCatching { Image.makeFromEncoded(cached.readBytes()).toComposeImageBitmap() }
            .getOrNull()
        // Only on a hit: marking an entry the decode then rejected would keep an
        // unreadable file alive at the young end of the sweep's ordering forever.
        if (bitmap != null) markUsed(cached)
        return bitmap
    }

    /**
     * The one expensive pass, paid once per file ever: decode at full size,
     * scale down, write the entry.
     *
     * **The edit is folded in here and never on [readCached]'s path**, which is
     * already the edited picture: the disk entry is keyed on path, size *and*
     * mtime, all three of which writing a recipe changes, so an edited file simply
     * misses its old thumbnail and rebuilds this once (ME-133). Applying on the
     * cached path too would fold the recipe over a picture that already carries it.
     */
    private fun build(file: File, cached: File, frameIndex: Int): ImageBitmap {
        val source = AppliedStillEdits.apply(file, frameIndex, decodeSmall(file, frameIndex))
        val scaled = source.scaledToPreview()
        runCatching { cached.writeBytes(scaled.encodeWebp()) }
        return scaled.toComposeImageBitmap()
    }

    /**
     * A tiled HEIF is assembled *at* preview size rather than assembled whole
     * and then shrunk — the difference between allocating 0.5 MB and 138 MB.
     * Every other decoder has no such option and hands over the full picture,
     * which for the formats they read is small enough not to matter.
     */
    private fun decodeSmall(file: File, frameIndex: Int): ImageBitmap {
        val decoder = stillDecoderFor(file)
        return if (decoder is FfmpegStillDecoder) {
            decoder.frame(file, frameIndex, PREVIEW_MAX_PX)
        } else {
            decoder.frame(file, frameIndex)
        }
    }
}

/**
 * Scaled on the GPU-shaped path rather than through AWT: `toAwtImage` on a
 * 36-megapixel bitmap copies 138 MB onto the JVM heap, which is a real risk of
 * running the app out of it for something that is about to become 400 px.
 */
private fun ImageBitmap.scaledToPreview(): Image {
    val source = Image.makeFromBitmap(asSkiaBitmap())
    val scale = PREVIEW_MAX_PX.toDouble() / max(width, height)
    if (scale >= 1.0) return source
    val targetWidth = max(1, (width * scale).roundToInt())
    val targetHeight = max(1, (height * scale).roundToInt())
    return Surface.makeRasterN32Premul(targetWidth, targetHeight).use { surface ->
        surface.canvas.drawImageRect(
            source,
            Rect.makeWH(width.toFloat(), height.toFloat()),
            Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat())
        )
        surface.makeImageSnapshot()
    }
}
