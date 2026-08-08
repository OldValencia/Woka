package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.ImageFrameSet
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReader

/**
 * Multi-page TIFF, through the JDK's own `ImageIO`.
 *
 * It is here because it is the one decoder that costs nothing to adopt — no
 * dependency, no native library, already on the classpath — and because TIFF is
 * the only format in this feature's corpus that nothing else pages through:
 * skia has no TIFF codec at all, and FFmpeg's `tiff_pipe` demuxer reports one
 * frame for a three-page file. The JDK's TIFF plugin (JDK 9+) reports three.
 *
 * `ImageIO` also reads animated GIF, and is deliberately not given it: skia
 * reports per-frame durations, which `ImageIO` does not surface.
 */
internal object ImageIoStillDecoder : StillDecoder {

    /** Per page: TIFF pages need not share a size. */
    override fun nativeSize(file: File, frameIndex: Int): IntSize =
        onReader(file) { IntSize(it.getWidth(frameIndex), it.getHeight(frameIndex)) }

    /**
     * `getNumImages(true)` — the `true` matters. It permits the reader to scan
     * the stream for further images, which is the whole question for a
     * multi-page TIFF; without it the reader answers 1 for everything it opens.
     */
    override fun frameSet(file: File): ImageFrameSet = onReader(file) { reader ->
        val count = reader.getNumImages(true)
        if (count <= 1) return@onReader ImageFrameSet.Single
        ImageFrameSet(
            (0 until count).map { page ->
                ImageFrame(
                    index = page,
                    kind = if (page == 0) FrameKind.Primary else FrameKind.Sequence
                )
            }
        )
    }

    /**
     * Unlike the FFmpeg path, `read` hands back a fully materialised heap
     * raster, so the conversion is safe after the reader and its stream close.
     */
    override fun frame(file: File, frameIndex: Int): ImageBitmap =
        onReader(file) { it.read(frameIndex) }.toStillBitmap()
}

private fun <T> onReader(file: File, block: (ImageReader) -> T): T {
    val stream = ImageIO.createImageInputStream(file)
        ?: error("no ImageIO stream for ${file.name}")
    return stream.use {
        val readers = ImageIO.getImageReaders(stream)
        check(readers.hasNext()) { "no ImageIO reader for ${file.name}" }
        val reader = readers.next()
        try {
            reader.input = stream
            block(reader)
        } finally {
            reader.dispose()
        }
    }
}
