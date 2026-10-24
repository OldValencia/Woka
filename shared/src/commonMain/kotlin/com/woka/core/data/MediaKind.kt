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

/**
 * Camera RAW, all read by LibRaw — public because the decoder routing needs the
 * same set, and a second copy of 32 names is how the two would drift. Absent:
 * `rwz` (Rawzor), which LibRaw has no decoder for, and `ari` (ARRI), which the
 * bundled LibRaw 0.21.2 refuses as "Unsupported file format" (measured on an
 * Alexa Mini sample, 2026-10-24).
 */
val RAW_IMAGE_EXTENSIONS = setOf(
    "dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "sr2", "raf", "orf", "ori",
    "rw2", "pef", "ptx", "3fr", "fff", "iiq", "cap", "rwl", "bay", "dcr", "dcs",
    "drf", "k25", "kdc", "erf", "mef", "mos", "mrw", "mdc", "pxn", "raw"
)

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif", "tif", "tiff", "svg",
    "tga", "qoi", "jp2", "pcx", "ppm",
    "psd", "psdt", "pdd",
    "jfif", "jpe", "dib", "rle",
    "hif", "pgm", "pbm",
    "exr", "dds", "xbm", "xpm", "pnm",
    "psb", "ico", "cur",
    "paint", "hdr", "ai", "jxl", "jxr", "wdp"
) + RAW_IMAGE_EXTENSIONS

/**
 * Image formats that Woka can only display in read-only mode (composite view),
 * without any editing capabilities.
 */
private val READONLY_IMAGE_EXTENSIONS = setOf("psd", "psdt", "pdd", "psb", "paint", "ai")

val File.isReadOnlyMedia: Boolean
    get() = extension.lowercase() in READONLY_IMAGE_EXTENSIONS

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "webm",
    "m4v", "3gp", "mpg", "mpeg", "m2ts", "mts", "wmv", "flv",
    "asf", "divx", "3g2", "mpeg1", "mpeg2", "mpeg4", "mpg4", "mpv", "mpv2",
    "ts", "vob", "vro", "mxf", "f4v", "mk3d", "tod", "tp", "rec", "evo",
    "gvi", "drc", "dv"
)

/** The complete set of image and video file extensions supported by Woka. */
val ALL_SUPPORTED_MEDIA_EXTENSIONS: Set<String>
    get() = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS

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
private val COIL_BLIND_EXTENSIONS =
    setOf(
        "heic", "heif", "avif", "tif", "tiff", "svg", "tga", "qoi", "jp2", "pcx", "ppm",
        "psd", "psdt", "pdd",
        "hif", "pgm", "pbm",
        "exr", "dds", "xbm", "xpm", "pnm",
        "psb", "ico", "cur",
        "paint", "hdr", "ai", "jxl", "jxr", "wdp"
    ) + RAW_IMAGE_EXTENSIONS

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
    setOf("heic", "heif", "hif", "avif", "gif", "webp", "tif", "tiff", "ico", "cur", "jxl")

val File.mayHoldFrames: Boolean
    get() = extension.lowercase() in FRAME_CAPABLE_EXTENSIONS

/** The only place a [MediaItem] learns whether it is an image or a video. */
fun mediaItemOrNull(file: File): MediaItem? = when (file.mediaKind()) {
    MediaKind.Image -> MediaItem.Image(file)
    MediaKind.Video -> MediaItem.Video(file)
    null -> null
}
