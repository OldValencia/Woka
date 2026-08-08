package com.woka.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Skia's PNG `quality` is a **compression-effort** knob, not a fidelity one —
 * PNG is lossless at every setting, so this only trades time for bytes. The
 * default is 100, which is the maximum, and on a large picture that default is
 * ruinous.
 *
 * Measured on a 6016×6016 export (36 megapixels):
 *
 * | quality | time | size |
 * |---|---|---|
 * | 100 (the default) | **33.8 s** | 30.5 MB |
 * | 80 | 21.5 s | 30.8 MB |
 * | 50 | 2.6 s | 33.7 MB |
 * | **20** | **1.4 s** | 35.3 MB |
 * | 0 | 0.7 s | 141.6 MB — no compression at all |
 *
 * Thirty-four seconds to save 14% over a setting that takes one and a half.
 * Twenty is chosen for being past the cliff at zero and before the wall above
 * fifty; for comparison `ImageIO` writes the same picture in 2.3 s at 42.5 MB,
 * so this is faster *and* smaller than the obvious alternative, without copying
 * the pixels onto the JVM heap to get there.
 */
private const val PNG_COMPRESSION_EFFORT = 20

internal fun Image.encodePng(): ByteArray =
    encodeToData(EncodedImageFormat.PNG, PNG_COMPRESSION_EFFORT)?.bytes
        ?: error("PNG encode failed")

internal fun ImageBitmap.encodePng(): ByteArray = Image.makeFromBitmap(asSkiaBitmap()).encodePng()
