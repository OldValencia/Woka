package com.woka.core.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import kotlinx.coroutines.Dispatchers

/** Overridable for tests, the same way `woka.storageRoot` is (see `WokaStorage`). */
const val IMAGE_CACHE_BUDGET_PROPERTY = "woka.imageCacheBytes"

/** 256 MB of decoded pixels. See [ImageBitmapCache] for why that number. */
private const val DEFAULT_BUDGET_BYTES = 256L * 1024 * 1024

/**
 * Decode cache shared by the viewer's still loader and the video poster-frame
 * extractor. Split out of `ThumbnailLoader` when that object was divided between
 * `viewer/image` and `viewer/video/engine`.
 *
 * **Least-recently-used, bounded by bytes** (F1). It used to be a
 * `ConcurrentHashMap` with no eviction at all, and the two populations in here
 * differ by three orders of magnitude — a video poster is ≤ 400 px (~0.36 MB)
 * while a still is cached at *native* resolution (96 MB for a 24 MP photo). A cap
 * on the number of entries would therefore have bounded nothing: a hundred of them
 * is anywhere between 36 MB and 20 GB. It is also not the JVM heap being spent —
 * Skia holds the pixels natively, so `-Xmx` does not bound this and the collector
 * never feels the pressure.
 *
 * One shared budget rather than a bucket per population: a poster evicted by a
 * large photo is re-read from its PNG in `thumbnailsDir`, which is cheap, whereas
 * a still has no second-level cache and re-decoding it is the expensive case — so
 * the one policy that keeps recent stills is the one that wants keeping. 256 MB
 * holds two or three full-size photos, which is what paging back and forth by one
 * actually asks for, plus several hundred posters.
 */
object ImageBitmapCache {

    /** True LRU: `accessOrder` moves a read entry to the young end. */
    private val entries = LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)

    private val lock = Any()

    /** Decoded bytes currently held. Read it to measure — TC-PERF-04 does. */
    var retainedBytes: Long = 0L
        get() = synchronized(lock) { field }
        private set

    val budgetBytes: Long
        get() = System.getProperty(IMAGE_CACHE_BUDGET_PROPERTY)?.toLongOrNull()
            ?: DEFAULT_BUDGET_BYTES

    /** Two decodes at a time, as the loader has always throttled them. */
    val ioDispatcher = Dispatchers.IO.limitedParallelism(2)

    /**
     * The cache key for one frame of one file.
     *
     * Keyed by absolute path alone, a twelve-frame burst at native resolution
     * would be twelve collisions on one key — every frame overwriting the last
     * and the strip showing whichever decoded most recently. **Frame 0 keeps the
     * bare path** so nothing already cached is invalidated and every existing
     * caller is untouched. The LRU budget then does the right thing without
     * being told: paging through a burst evicts the frames behind it.
     */
    fun key(path: String, frameIndex: Int): String =
        if (frameIndex == 0) path else "$path#$frameIndex"

    /**
     * A still's carousel-sized copy, which must **not** share the bare path with
     * its native-resolution one — the viewer draws that same key full screen,
     * and handing it a 400 px preview would show a blurred picture.
     */
    fun previewKey(path: String, frameIndex: Int = 0): String =
        if (frameIndex == 0) "$path#preview" else "$path#preview$frameIndex"

    operator fun get(key: String): ImageBitmap? = synchronized(lock) { entries[key] }

    operator fun set(key: String, bitmap: ImageBitmap) = synchronized(lock) {
        entries.put(key, bitmap)?.let { retainedBytes -= it.byteSize }
        retainedBytes += bitmap.byteSize
        evictDownToBudget(except = key)
    }

    /**
     * Forgets one entry, so the next read decodes again.
     *
     * The cache had **no removal at all** until an in-place overwrite needed one
     * (measured 2026-08-17): every other population here is keyed on something that
     * changes when the file does — the `.thumbnails` poster carries length and mtime
     * in its key and simply orphans — but these two are keyed on the bare path, so
     * writing over a picture leaves the pre-edit pixels on screen until something
     * else evicts them.
     *
     * Silent on a key that is not there. An overwrite invalidates both the native and
     * the preview entry without knowing which of them was ever populated, and making
     * that the caller's problem would put the same `if` at every call site.
     */
    fun remove(key: String) = synchronized(lock) {
        entries.remove(key)?.let { retainedBytes -= it.byteSize }
        Unit
    }

    /**
     * Forgets **every** frame of one file — native and preview, frame 0 and the rest.
     *
     * A recipe belongs to the file, not to the frame that was open when it was written
     * (ME-108 made frame sets editable for the first time), so a save changes what all
     * of them draw as. Removing only the edited frame's two keys left a burst's other
     * thumbnails showing the picture from before the edit until something remounted
     * them — found by hand on a multi-frame HEIC, 2026-08-19.
     *
     * Matched on the key shapes [key] and [previewKey] build: the bare path, or the
     * path followed by `#`.
     */
    fun removeAllFor(path: String) = synchronized(lock) {
        val ours = entries.keys.filter { it == path || it.startsWith("$path#") }
        ours.forEach { key -> entries.remove(key)?.let { retainedBytes -= it.byteSize } }
    }

    /**
     * Drops the oldest entries until the budget is met. [except] is the entry just
     * admitted: a single photo larger than the whole budget still has to be
     * drawable, so it is kept and the cache simply holds nothing else.
     */
    private fun evictDownToBudget(except: String) {
        val oldestFirst = entries.entries.iterator()
        while (retainedBytes > budgetBytes && oldestFirst.hasNext()) {
            val entry = oldestFirst.next()
            if (entry.key == except) continue
            retainedBytes -= entry.value.byteSize
            oldestFirst.remove()
        }
    }
}

/**
 * What one entry costs. Everything reaching this cache is `N32Premul` — both
 * `Image.makeFromEncoded` and the poster's surface snapshot produce it — but the
 * config is read rather than assumed, so an accounting that silently halves is not
 * one toolchain change away.
 */
private val ImageBitmap.byteSize: Long
    get() {
        val bytesPerPixel = when (config) {
            ImageBitmapConfig.Alpha8 -> 1
            ImageBitmapConfig.Rgb565 -> 2
            ImageBitmapConfig.F16 -> 8
            else -> 4
        }
        return width.toLong() * height * bytesPerPixel
    }
