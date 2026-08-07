package com.woka.probe

import java.io.File
import javax.imageio.ImageIO
import javax.imageio.stream.ImageInputStream

/**
 * What the JDK's own `ImageIO` makes of a file — asked because it is the one
 * decoder that costs nothing to adopt: no dependency, no native library, already
 * on the classpath. Since JDK 9 it ships a TIFF plugin, and TIFF is the only
 * format in this corpus that neither skia nor FFmpeg pages through.
 */
data class ImageIoReport(
    val reader: String = "—",
    val imageCount: Int = 0,
    val size: String = "—",
    val failure: String? = null
)

/**
 * `getNumImages(true)` — the `true` matters: it allows the reader to scan the
 * stream for further images, which is the whole question for a multi-page TIFF.
 * Without it the reader answers 1 for everything it can open.
 */
fun probeImageIo(file: File): ImageIoReport = runCatching {
    ImageIO.createImageInputStream(file).use { stream ->
        readerReport(stream) ?: ImageIoReport(failure = "no reader")
    }
}.getOrElse { ImageIoReport(failure = it.brief()) }

private fun readerReport(stream: ImageInputStream): ImageIoReport? {
    val readers = ImageIO.getImageReaders(stream)
    if (!readers.hasNext()) return null
    val reader = readers.next()
    return try {
        reader.input = stream
        ImageIoReport(
            reader = reader.formatName,
            imageCount = reader.getNumImages(true),
            size = "${reader.getWidth(0)}×${reader.getHeight(0)}"
        )
    } catch (error: Throwable) {
        ImageIoReport(reader = runCatching { reader.formatName }.getOrDefault("—"),
            failure = error.brief())
    } finally {
        reader.dispose()
    }
}
