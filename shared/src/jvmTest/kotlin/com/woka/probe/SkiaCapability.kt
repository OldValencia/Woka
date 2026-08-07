package com.woka.probe

import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import java.io.File

/** How many per-frame durations the report prints before it gives up on width. */
private const val LISTED_DURATIONS = 6

/**
 * What skia can tell us about one file.
 *
 * [frameCount] is the number the app does not currently ask for —
 * `Image.makeFromEncoded` collapses a frame set to its first frame silently,
 * which is why an animated GIF sits still today.
 */
data class SkiaReport(
    val frameCount: Int = 0,
    val durationsMs: List<Int> = emptyList(),
    val size: String = "—",
    val colorSpace: String = "—",
    val imageFailure: String? = null,
    val failure: String? = null
) {
    val opened: Boolean get() = failure == null
}

/**
 * `Codec` rather than `Image`, because only the codec counts frames. Both are
 * asked, and asked **independently**, because the viewer's path today is
 * `Image.makeFromEncoded`: a format one opens is not automatically one the
 * other survives, in either direction.
 */
fun probeSkia(file: File): SkiaReport {
    val bytes = file.readBytes()
    val image = imageFailure(bytes)
    return runCatching {
        Data.makeFromBytes(bytes).use { data ->
            Codec.makeFromData(data).use { codec ->
                SkiaReport(
                    frameCount = codec.frameCount,
                    durationsMs = frameDurations(codec),
                    size = "${codec.width}×${codec.height}",
                    colorSpace = describeColorSpace(codec.colorSpace),
                    imageFailure = image
                )
            }
        }
    }.getOrElse { SkiaReport(imageFailure = image, failure = it.brief()) }
}

/**
 * Empty for a still, because asking is not safe there: on a single-frame codec
 * skia leaves the animation fields uninitialised and `getFrameInfo(0)` throws
 * `IndexOutOfBoundsException: index: 1824733184, size: 4` out of the enum
 * decode. Only a real frame set has frame info to read.
 */
private fun frameDurations(codec: Codec): List<Int> {
    if (codec.frameCount <= 1) return emptyList()
    return (0 until minOf(codec.frameCount, LISTED_DURATIONS))
        .map { runCatching { codec.getFrameInfo(it).duration }.getOrDefault(-1) }
}

/** Null when `Image.makeFromEncoded` — the viewer's own call — succeeds. */
private fun imageFailure(bytes: ByteArray): String? =
    runCatching { Image.makeFromEncoded(bytes).close() }.exceptionOrNull()?.brief()

/**
 * Everything skia exposes about a colour space, which is not much: three
 * booleans and no transfer function. Phase 5 needs to know that before it
 * plans to read a still's transfer characteristic off this path.
 */
private fun describeColorSpace(space: ColorSpace?): String = when {
    space == null -> "null"
    space.isSRGB -> "sRGB"
    space.isGammaLinear -> "linear"
    space.isGammaCloseToSRGB -> "≈sRGB gamma, non-sRGB gamut"
    else -> "non-sRGB"
}

/** FFmpeg quotes the absolute path it failed on, which is noise in a committed table. */
private val QUOTED = Regex("\"[^\"]*\"")

/**
 * JavaCV appends `(Has setFormat() been called?)` and friends to every open
 * failure. It is never the reason for one here, and it buries the AVERROR code
 * that is.
 */
private const val JAVACV_HINT = " (Has "

internal fun Throwable.brief(): String {
    val text = message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        .replace(QUOTED, "…")
        .substringBefore(JAVACV_HINT)
    val name = this::class.simpleName ?: "Throwable"
    return if (text.isEmpty()) name else "$name: ${text.take(80)}"
}
