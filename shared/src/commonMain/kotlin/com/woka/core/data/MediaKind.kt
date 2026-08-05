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

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic")

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm")

/**
 * Listed and counted, but no decoder in the app reads them yet: Skia's
 * `Image.makeFromEncoded` does not do HEIF, and neither does Coil in the grid.
 * Kept in [IMAGE_EXTENSIONS] deliberately — being consistently "an image we cannot
 * open yet" beats being inconsistently invisible. A HEIF decoder is its own plan.
 */
private val UNDECODABLE_EXTENSIONS = setOf("heic")

fun File.mediaKind(): MediaKind? = when (extension.lowercase()) {
    in IMAGE_EXTENSIONS -> MediaKind.Image
    in VIDEO_EXTENSIONS -> MediaKind.Video
    else -> null
}

val File.isMedia: Boolean
    get() = mediaKind() != null

/** False for a format the app lists but cannot turn into pixels — see [UNDECODABLE_EXTENSIONS]. */
val File.canDecode: Boolean
    get() = extension.lowercase() !in UNDECODABLE_EXTENSIONS

/** The only place a [MediaItem] learns whether it is an image or a video. */
fun mediaItemOrNull(file: File): MediaItem? = when (file.mediaKind()) {
    MediaKind.Image -> MediaItem.Image(file)
    MediaKind.Video -> MediaItem.Video(file)
    null -> null
}
