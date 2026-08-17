package com.woka.feature.editor.save

import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * The three formats Skia can write, and the quality each is written at.
 *
 * Eleven extensions reach the editor and **three** can be saved back in place. The
 * other eight are not a gap to be filled here: `bmp`, `gif`, `tif` and `tiff` have no
 * Skia encoder; `heic` and `heif` cannot be written by anything in the build, because
 * `libx265` cannot open a HEIF output at all (measured, ME-010); and `avif` has a road
 * through ffmpeg's `libaom-av1` that belongs to the HDR tool, not to a raster save.
 *
 * [formatFor] returning null is therefore a **refusal**, and ME-108 is what turns a
 * refusal into a sheet offering a target the user agrees to. That is the whole reason
 * the Edit button can be honest about eight of eleven formats without ever being dead.
 */
internal enum class SaveFormat(
    val extension: String,
    val skia: EncodedImageFormat,
    val quality: Int
) {
    /** Lossless — ME-010 measured a zero-delta round trip. */
    PNG("png", EncodedImageFormat.PNG, 100),

    /**
     * 95, not 90 (§9). Editing is a second compression laid over the source's first,
     * and at 90 the artefacts of the two stack visibly. The file is about a third
     * larger, which is the trade this project chose.
     */
    JPEG("jpg", EncodedImageFormat.JPEG, 95),

    WEBP("webp", EncodedImageFormat.WEBP, 90);

    /** Whether the source's metadata can survive a re-encode at all (ME-010). */
    val carriesExif: Boolean get() = this == JPEG
}

internal fun formatFor(file: File): SaveFormat? = when (file.extension.lowercase()) {
    "png" -> SaveFormat.PNG
    // Both spellings encode as JPEG; the *source's* own extension is kept when
    // overwriting, so a `.jpeg` does not silently become a `.jpg`.
    "jpg", "jpeg" -> SaveFormat.JPEG
    "webp" -> SaveFormat.WEBP
    else -> null
}
