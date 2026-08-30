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
 *
 * **All three write at 100, and there is no setting** (§9, 2026-08-30). Editing is a
 * second compression laid over the source's first, the file is the user's own
 * photograph, and the ceiling is the only value that never has to be defended — a
 * number left where somebody once put it is paid for in a generation of a picture that
 * cannot be got back. WebP's 100 is *lossy at maximum*, not lossless: Skia's
 * `encodeToData` offers no lossless-WebP road at all.
 */
internal enum class SaveFormat(
    val extension: String,
    val skia: EncodedImageFormat,
    val quality: Int
) {
    /** Lossless — ME-010 measured a zero-delta round trip. */
    PNG("png", EncodedImageFormat.PNG, 100),

    JPEG("jpg", EncodedImageFormat.JPEG, 100),

    WEBP("webp", EncodedImageFormat.WEBP, 100);

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
