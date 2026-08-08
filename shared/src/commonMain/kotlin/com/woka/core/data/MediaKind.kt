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
 * Formats the **grid** cannot draw. The viewer opens every one of them.
 *
 * This set used to mean "no decoder anywhere in the app", and `.heic` was in it
 * because nothing could read a HEIF. That is no longer the case — `StillDecoders`
 * routes HEIF and AVIF to FFmpeg and TIFF to `ImageIO` — but the grid does not go
 * through `StillDecoders`. It goes through Coil, which decodes what the platform
 * decodes and so still draws an empty tile for all five. They stay in
 * [IMAGE_EXTENSIONS] deliberately: being consistently "an image the grid cannot
 * preview" beats being inconsistently invisible.
 *
 * Emptied by the `WokaImageDecoder` step, which puts Coil on the same routing.
 */
private val GRID_UNDECODABLE_EXTENSIONS = setOf("heic", "heif", "avif", "tif", "tiff")

fun File.mediaKind(): MediaKind? = when (extension.lowercase()) {
    in IMAGE_EXTENSIONS -> MediaKind.Image
    in VIDEO_EXTENSIONS -> MediaKind.Video
    else -> null
}

val File.isMedia: Boolean
    get() = mediaKind() != null

/**
 * False for a format the grid lists but cannot preview — see
 * [GRID_UNDECODABLE_EXTENSIONS]. Not a question about the viewer, which opens
 * every listed format; ask `stillDecoderFor` about that one.
 */
val File.canDecodeInGrid: Boolean
    get() = extension.lowercase() !in GRID_UNDECODABLE_EXTENSIONS

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
