package com.woka.feature.viewer.image

import java.io.File

/** A viewer session walks a handful of files; the rest can be re-read. */
private const val REMEMBERED_FILES = 8

/**
 * Identity of a file's *contents*, so a file replaced in place is not answered
 * from the old reading. The same three fields `WokaStorage.thumbnailFile` keys
 * its disk cache on.
 */
private data class LayoutKey(val path: String, val length: Long, val modified: Long)

private val layouts = object : LinkedHashMap<LayoutKey, FfmpegStillLayout>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<LayoutKey, FfmpegStillLayout>) =
        size > REMEMBERED_FILES
}

private val lock = Any()

/**
 * Remembers what [readStillLayout] found, because three separate questions ask
 * for it about the same file: what frames are in here, how big is the picture,
 * and give me picture *n*. Each one used to open the container again, and on a
 * 1296-stream HEIC an open is not free.
 *
 * Bounded and least-recently-used, like `ImageBitmapCache` — an unbounded map
 * keyed by path is a leak in a long session over a large library.
 */
internal fun cachedLayout(file: File, read: () -> FfmpegStillLayout): FfmpegStillLayout {
    val key = LayoutKey(file.absolutePath, file.length(), file.lastModified())
    synchronized(lock) { layouts[key] }?.let { return it }
    val layout = read()
    synchronized(lock) { layouts[key] = layout }
    return layout
}
