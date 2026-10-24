package com.woka.feature.viewer.image

import java.io.File
import javax.imageio.ImageReader

/**
 * Icon containers: every entry is the same picture at another size, stored in
 * whatever order the authoring tool chose — usually smallest first, so frame 0
 * would be a 16×16 thumbnail of a 256×256 icon.
 */
private val ICON_EXTENSIONS = setOf("ico", "cur")

/**
 * TwelveMonkeys' PSD reader counts the composite **plus one image per layer**
 * (measured 2026-10-24: a one-layer `.psb` reports 2). `.psd` goes through FFmpeg
 * and shows the composite only; `.psb` does the same rather than growing a strip
 * of its layers.
 */
private val COMPOSITE_ONLY_EXTENSIONS = setOf("psb")

/**
 * The reader's own image indices, in the order the viewer shows them — frame *n*
 * is `imageIoPages(...)[n]`.
 *
 * Icons are sorted largest first, so frame 0 is what the grid tile and the
 * viewer open on. The sort is stable: equal sizes keep the file's order.
 */
internal fun imageIoPages(file: File, reader: ImageReader): List<Int> {
    val extension = file.extension.lowercase()
    if (extension in COMPOSITE_ONLY_EXTENSIONS) return listOf(0)
    val pages = (0 until reader.getNumImages(true)).toList()
    if (extension !in ICON_EXTENSIONS) return pages
    return pages.sortedByDescending { reader.getWidth(it).toLong() * reader.getHeight(it) }
}

/**
 * The reader index behind frame [frameIndex]. Only icons are reordered, so a TIFF
 * answers without counting its pages — `getNumImages(true)` scans the whole file.
 */
internal fun imageIoPage(file: File, reader: ImageReader, frameIndex: Int): Int =
    if (file.extension.lowercase() in ICON_EXTENSIONS) {
        imageIoPages(file, reader)[frameIndex]
    } else {
        frameIndex
    }
