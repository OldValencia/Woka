package com.woka.feature.viewer.video.engine.preview

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Enough to hold any grid [SeekPreviewGrid] can produce, so within one file a cell
 * is decoded at most once. At 240×135 that is 62MB in the worst case.
 */
private const val CELL_ENTRIES = 512

/** Just enough that stepping back to a spot inspected seconds ago is instant. */
private const val EXACT_ENTRIES = 16

/**
 * The two preview populations, kept apart because they are keyed differently and
 * evicted at completely different rates: grid cells are the sweep's working set and
 * want to survive it, while exact refines are one-offs.
 *
 * Written from the preview thread and read from composition, hence the lock. The
 * maps are tiny and the critical sections are a get or a put, so contention is not
 * a consideration.
 */
internal class SeekPreviewCache {

    private val lock = Any()
    private val cells = lru<Int>(CELL_ENTRIES)
    private val exacts = lru<Long>(EXACT_ENTRIES)

    fun cell(index: Int): ImageBitmap? = synchronized(lock) { cells[index] }

    fun putCell(index: Int, bitmap: ImageBitmap) {
        synchronized(lock) { cells[index] = bitmap }
    }

    fun exact(key: Long): ImageBitmap? = synchronized(lock) { exacts[key] }

    fun putExact(key: Long, bitmap: ImageBitmap) {
        synchronized(lock) { exacts[key] = bitmap }
    }

    /** `accessOrder = true`, so a read moves an entry to the young end. */
    private fun <K> lru(max: Int): LinkedHashMap<K, ImageBitmap> =
        object : LinkedHashMap<K, ImageBitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, ImageBitmap>): Boolean =
                size > max
        }
}
