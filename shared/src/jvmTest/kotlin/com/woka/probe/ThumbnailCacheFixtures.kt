package com.woka.probe

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The populations [ThumbnailCacheProbe] measures over, and the one measurement that is
 * about bytes rather than milliseconds. Split from the probe under the §2 file-size
 * mandate.
 */

/** What `StillPreview` writes today: PNG at Skia's compression effort 20. */
private const val PNG_EFFORT = 20

/** [tiles] distinct paths over one fixture: distinct keys, identical decode cost. */
internal fun copies(root: File, stem: String, source: File, tiles: Int): List<File> {
    val folder = File(root, stem).apply { mkdirs() }
    return (0 until tiles).map { index ->
        File(folder, "$stem-$index.${source.extension}").also { source.copyTo(it, overwrite = true) }
    }
}

/**
 * A still the size of a photograph off a phone, because the committed corpus tops out
 * at 320×240 and the whole question is how the build cost scales with pixels.
 *
 * A smooth gradient rather than noise: it encodes to a small file while still forcing
 * a full-size decode, which is the pass being measured. PNG is not a format the grid
 * routes through `StillPreview` — that is `needsWokaCover`'s list — but `loadStillCover`
 * reaches the same builder for any still, so the pixel scaling it measures is the real
 * one. What is **not** measured is HEIC's own decoder being slower than PNG's, which
 * only moves the number further in the same direction.
 */
internal fun photoSizedStill(root: File, width: Int, height: Int): File {
    val file = File(root, "photo-sized.png")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            image.setRGB(x, y, (x * 255 / width shl 16) or (y * 255 / height shl 8) or 0x40)
        }
    }
    ImageIO.write(image, "png", file)
    return file
}

/**
 * What one poster costs on disk in each format Skia can write.
 *
 * Measured over **real photographic content** — the gradient above would flatter PNG
 * enormously, and flattering the format already in use is the one bias that would make
 * this measurement worthless. Alpha is why WebP and not JPEG is the interesting column:
 * `looksBlank` exists because a fully transparent PNG is a real thing to find in a
 * folder, and JPEG cannot carry that at all.
 */
internal fun encodedSizes(source: File, maxPx: Int): List<List<String>> {
    val preview = previewSized(Image.makeFromEncoded(source.readBytes()), maxPx)
    val formats = listOf(
        Triple("PNG (today)", EncodedImageFormat.PNG, PNG_EFFORT),
        Triple("JPEG q85", EncodedImageFormat.JPEG, 85),
        Triple("WEBP q80", EncodedImageFormat.WEBP, 80)
    )
    val baseline = preview.encodeToData(EncodedImageFormat.PNG, PNG_EFFORT)?.bytes?.size ?: 0
    return formats.map { (name, format, quality) ->
        val started = System.nanoTime()
        val bytes = preview.encodeToData(format, quality)?.bytes?.size ?: 0
        val micros = (System.nanoTime() - started) / 1_000
        listOf(name, "$bytes bytes", "${percentOf(bytes, baseline)} of PNG", "$micros µs")
    }
}

private fun percentOf(bytes: Int, baseline: Int): String =
    if (baseline == 0) "—" else "${bytes * 100 / baseline} %"

/** The same scale-to-longest-edge `StillPreview` applies before it writes. */
private fun previewSized(source: Image, maxPx: Int): Image {
    val scale = maxPx.toDouble() / max(source.width, source.height)
    if (scale >= 1.0) return source
    val width = max(1, (source.width * scale).roundToInt())
    val height = max(1, (source.height * scale).roundToInt())
    return Surface.makeRasterN32Premul(width, height).use { surface ->
        surface.canvas.drawImageRect(
            source,
            Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
            Rect.makeWH(width.toFloat(), height.toFloat())
        )
        surface.makeImageSnapshot()
    }
}

/** How long a startup sweep would spend walking a directory left to grow. */
internal fun timeSweep(dir: File, entries: Int): Long {
    dir.mkdirs()
    val filler = ByteArray(256)
    repeat(entries) { File(dir, "%05x.png".format(it)).writeBytes(filler) }
    val started = System.nanoTime()
    dir.listFiles().orEmpty().map { it.length() to it.lastModified() }.sortedBy { it.second }
        .sumOf { it.first }
    return (System.nanoTime() - started) / 1_000_000
}
