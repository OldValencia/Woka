package com.woka.core.data

import java.io.File

/**
 * What kind of media a file is — the single answer in the app.
 *
 * The sets below used to be written out six times across four files and disagreed
 * with each other: `.gif`/`.heic` were filtered and counted but never returned by
 * [LocalMediaRepository], and `.bmp` was returned but not counted, so a folder of
 * `.bmp` files read "0 items" and still opened non-empty. Nothing else may declare
 * an extension list.
 */
enum class MediaKind { Image, Video }

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif", "tif", "tiff"
)

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm")

/**
 * Formats **Coil** cannot read, and the grid draws with `AsyncImage`.
 *
 * The set has meant three different things in three days, and only the last one
 * is true: first "no decoder anywhere in the app", then "the grid cannot show
 * these at all", and now — since the grid falls back to `loadStillCover` for
 * them — simply "Coil is blind to this one, use ours". Nothing is hidden from
 * the grid any more; these just take a different road to the same tile.
 *
 * `.gif` and `.webp` are absent because Coil reads both, animation included.
 */
private val COIL_BLIND_EXTENSIONS = setOf("heic", "heif", "avif", "tif", "tiff")

fun File.mediaKind(): MediaKind? = when (extension.lowercase()) {
    in IMAGE_EXTENSIONS -> MediaKind.Image
    in VIDEO_EXTENSIONS -> MediaKind.Video
    else -> null
}

val File.isMedia: Boolean
    get() = mediaKind() != null

/**
 * True when a grid tile must be drawn by Woka's own decoders rather than by
 * Coil — see [COIL_BLIND_EXTENSIONS]. Not a question about the viewer, which
 * opens every listed format; ask `stillDecoderFor` about that one.
 */
val File.needsWokaCover: Boolean
    get() = extension.lowercase() in COIL_BLIND_EXTENSIONS

/**
 * Formats that *can* hold more than one picture — a burst, an animation, pages.
 *
 * Deliberately by name and not by opening the file: this decides whether a
 * context menu offers "Export Frames", and a grid would otherwise open every
 * HEIC in the folder to draw one menu. A file that turns out to hold a single
 * picture exports a single PNG, which is the honest answer rather than an error.
 */
private val FRAME_CAPABLE_EXTENSIONS =
    setOf("heic", "heif", "avif", "gif", "webp", "tif", "tiff")

val File.mayHoldFrames: Boolean
    get() = extension.lowercase() in FRAME_CAPABLE_EXTENSIONS

/** The only place a [MediaItem] learns whether it is an image or a video. */
fun mediaItemOrNull(file: File): MediaItem? = when (file.mediaKind()) {
    MediaKind.Image -> MediaItem.Image(file)
    MediaKind.Video -> MediaItem.Video(file)
    null -> null
}
