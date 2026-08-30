package com.woka.integration

import com.woka.core.ui.image.ImageBitmapCache
import com.woka.support.MEGABYTE
import com.woka.support.bitmapOf
import com.woka.support.withImageCacheBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Which entries the cache will hold at all (ME-123) — a different policy from the LRU
 * eviction beside it in `ImageCacheEvictionTest`, and its own file for that reason.
 *
 * Driven under `withImageCacheBudget` so what is tested is the **ratio** rather than
 * the megabytes: the shipped gate is half of whatever `woka.imageCacheBytes` says, and
 * a test written against 128 MB would be a test of one constant.
 */
class CacheAdmissionTest {

    private fun put(key: String, megabytes: Int = 1) {
        ImageBitmapCache[key] = bitmapOf(megabytes)
    }

    /**
     * **Reverses the 2026-08-17 reading** that a bitmap too big to cache "still has to
     * be drawable" (ME-123). It was never `set` that kept it drawable — the caller
     * already holds the bitmap it passed in — and admitting it evicted everything else
     * *and left the cache over budget*, which is the storm without the cache entry.
     */
    @Test
    fun `a bitmap over half the budget is refused, and evicts nothing`() =
        withImageCacheBudget(4 * MEGABYTE) {
            put("small")
            val before = ImageBitmapCache.retainedBytes

            put("huge", megabytes = 8)

            assertNull(ImageBitmapCache["huge"], "a 8 MB entry entered a 4 MB cache")
            assertNotNull(ImageBitmapCache["small"], "refusing an entry evicted a good one")
            assertEquals(before, ImageBitmapCache.retainedBytes)
        }

    /** The gate is a ratio, not a size: exactly half the budget still goes in. */
    @Test
    fun `an entry at half the budget is admitted as it always was`() {
        withImageCacheBudget(8 * MEGABYTE) {
            put("half", megabytes = 4)
            assertNotNull(ImageBitmapCache["half"])
        }
    }

    /**
     * A declined write must not leave the entry it was meant to replace behind: that
     * is a picture rewritten on disk still serving its pre-edit pixels.
     */
    @Test
    fun `a refused write drops whatever it was replacing`() = withImageCacheBudget(4 * MEGABYTE) {
        put("photo")
        assertNotNull(ImageBitmapCache["photo"], "the fixture never stored anything")

        put("photo", megabytes = 8)

        assertNull(ImageBitmapCache["photo"], "the stale entry outlived the decode replacing it")
    }
}
