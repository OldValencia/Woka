package com.woka.core.utils

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.ui.image.encodePng
import com.woka.feature.viewer.image.displayable
import com.woka.feature.viewer.image.stillDecoderFor
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun exportFramesToPng(
    source: File,
    destination: File,
    onProgress: (done: Int, total: Int) -> Unit
): FrameExportResult = withContext(ImageBitmapCache.ioDispatcher) {
    val decoder = stillDecoderFor(source)
    val frames = runCatching { decoder.frameSet(source).viewable }.getOrNull().orEmpty()
    val total = frames.size.coerceAtLeast(1)
    var exported = 0
    var failed = 0
    frames.indices.forEach { position ->
        onProgress(position, total)
        val written = runCatching {
            val frame = frames[position].index
            // An exported frame is the picture the user is looking at, edit included
            // (ME-133) — an export that quietly returned the original would be the one
            // place in the app where what is on screen and what is written disagree.
            val drawn = displayable(source, decoder.frame(source, frame))
            val bitmap = AppliedStillEdits.apply(source, frame, drawn)
            bitmap.writePng(File(destination, nameFor(source, position, total)))
        }.isSuccess
        if (written) exported++ else failed++
    }
    onProgress(total, total)
    FrameExportResult(exported, failed)
}

/**
 * `photo_01.png`, padded to the width the count needs so a folder of them sorts
 * the way they were shot. A single-frame file keeps its plain name — numbering
 * one picture reads as though something is missing.
 */
private fun nameFor(source: File, position: Int, total: Int): String {
    val stem = source.nameWithoutExtension
    if (total <= 1) return "$stem.png"
    val width = total.toString().length
    return "${stem}_${(position + 1).toString().padStart(width, '0')}.png"
}

/**
 * Encoded through Skia rather than `ImageIO`: the bitmap is already a Skia one,
 * and `toAwtImage` would copy a 36-megapixel picture onto the JVM heap on the
 * way out for no reason. The compression effort is [encodePng]'s to choose, and
 * choosing it is what took this from thirty seconds a frame to one and a half.
 */
private fun ImageBitmap.writePng(target: File) = target.writeBytes(encodePng())
