package com.woka.feature.editor.save

import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.ui.image.MediaInvalidation
import java.io.File

/**
 * What has to be forgotten after a save, for **every** save (§5.2).
 *
 * ME-104 evicted only on overwrite, because a copy was a path nothing had ever
 * cached. There are no copies any more: every save changes what an existing path
 * draws as, so every save evicts and the condition is gone.
 *
 * The `.thumbnails` poster is not here and does not need to be — it is keyed on
 * path, length and mtime, all three of which a write changes, so it orphans rather
 * than going stale.
 */
internal fun evictCachesFor(file: File) {
    val path = file.absolutePath
    // **Every frame, not the one that was edited.** The recipe is written once per
    // file, so a burst's other frames draw differently after a save too — they kept
    // their pre-edit thumbnails until something remounted the strip (2026-08-19).
    ImageBitmapCache.removeAllFor(path)
    evictCoil(file)
    // **Last, after every cache is clear** (ME-135). Dropping an entry changes nothing
    // on screen by itself; this is what makes a reader ask again. Raised before the
    // eviction above, a reader woken early would re-read the entry about to be dropped.
    MediaInvalidation.invalidate(path)
}

/**
 * The grid's road, which `ImageBitmapCache` knows nothing about.
 *
 * A `jpg` tile is drawn by Coil's `AsyncImage`, and its memory key for a local file
 * is the plain file URI — `addLastModifiedToFileCacheKey` defaults to **off**, so a
 * write does not change the key and the tile keeps the pre-edit picture until
 * something unrelated evicts it. That is the bug §5.2 names.
 *
 * **Observed, not inferred** (`SaveEvictionTest`, coil 3.5.0): the key comes out as
 * `file:C:\…\photo.jpg` — the raw Windows path, no percent-encoding, no `//`. It is
 * built by `FileMapper` from `File.path`, which is why both that and [File
 * .absolutePath] are matched: they are the same string for a file that came from a
 * directory listing, and neither is a substring of the other if one is ever not.
 *
 * Matched by substring rather than by rebuilding the key: one file yields one entry
 * per requested size and per set of extras, and a tile, a cover mosaic and a
 * carousel thumbnail of the same picture are three different keys sharing one path.
 *
 * **Coil's disk cache is deliberately not touched.** Measured against 3.5.0: only a
 * network fetcher writes to it, `coil-network-*` is not on the classpath at all, and
 * a local file is read by `FileUriFetcher` straight off the disk. There is no entry
 * to remove and no key that would name one.
 */
private fun evictCoil(file: File) {
    val loader = runCatching { SingletonImageLoader.get(PlatformContext.INSTANCE) }.getOrNull()
    val memory = loader?.memoryCache ?: return
    val names = setOf(file.path, file.absolutePath)
    memory.keys
        .filter { key -> names.any { key.key.contains(it) } }
        .forEach { memory.remove(it) }
}
