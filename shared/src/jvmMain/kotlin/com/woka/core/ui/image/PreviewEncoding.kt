package com.woka.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * How a `.thumbnails` entry is written. Separate from [encodePng], which stays what
 * it is: the encoder for a **frame the user asked to export**, where lossless is the
 * only defensible answer.
 *
 * A poster is not that. It is a 400 px copy of a picture the user already owns,
 * rebuilt for free if it is ever lost, and PNG spends its bytes preserving detail
 * nobody will look at. Measured 2026-08-31 by `ThumbnailCacheProbe` on one poster of
 * real photographic content:
 *
 * | format | on disk | against PNG | encode |
 * |---|---|---|---|
 * | PNG, effort 20 | 20 624 B | 100 % | 1.9 ms |
 * | JPEG q85 | 13 328 B | 64 % | 1.1 ms |
 * | **WebP q80** | **6 680 B** | **32 %** | 4.7 ms |
 *
 * Three times less disk for 2.8 ms more, paid once per file on a build that already
 * costs about thirty. **JPEG is not the cheaper option it looks like**: it reaches
 * only 64 % and cannot carry alpha at all, and a fully transparent picture is a real
 * thing to find in a folder — `looksBlank` exists because one was.
 */
private const val WEBP_QUALITY = 80

internal fun Image.encodeWebp(): ByteArray =
    encodeToData(EncodedImageFormat.WEBP, WEBP_QUALITY)?.bytes
        ?: error("WebP encode failed")

internal fun ImageBitmap.encodeWebp(): ByteArray = Image.makeFromBitmap(asSkiaBitmap()).encodeWebp()
