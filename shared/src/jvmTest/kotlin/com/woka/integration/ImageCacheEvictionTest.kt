package com.woka.integration

import com.woka.core.ui.image.ImageBitmapCache
import com.woka.support.MEGABYTE
import com.woka.support.bitmapOf
import com.woka.support.withImageCacheBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The F1 eviction policy: least-recently-used, bounded by bytes.
 *
 * Driven at a three-megabyte budget rather than the shipped 256 MB, so the cases
 * below cost megabytes instead of gigabytes and the arithmetic is legible. Every
 * bitmap here is a whole number of megabytes (`bitmapOf`).
 *
 * Each case fills the cache past the budget before asserting, which means the
 * entries other tests left in the singleton have been evicted by then — that is
 * why `retainedBytes` comes out exact rather than "whatever was already there
 * plus mine".
 */
class ImageCacheEvictionTest {

    private fun put(key: String, megabytes: Int = 1) {
        ImageBitmapCache[key] = bitmapOf(megabytes)
    }

    @Test
    fun `entries within the budget are all kept`() = withImageCacheBudget(3 * MEGABYTE) {
        put("a")
        put("b")
        put("c")
        assertNotNull(ImageBitmapCache["a"])
        assertNotNull(ImageBitmapCache["b"])
        assertNotNull(ImageBitmapCache["c"])
        assertEquals(3 * MEGABYTE, ImageBitmapCache.retainedBytes)
    }

    @Test
    fun `the oldest entry goes when the budget is exceeded`() = withImageCacheBudget(3 * MEGABYTE) {
        put("old")
        put("mid")
        put("new")
        put("newest")
        assertNull(ImageBitmapCache["old"], "the least recently used entry survived")
        assertNotNull(ImageBitmapCache["mid"])
        assertNotNull(ImageBitmapCache["newest"])
        assertEquals(3 * MEGABYTE, ImageBitmapCache.retainedBytes)
    }

    @Test
    fun `reading an entry makes it young again`() = withImageCacheBudget(3 * MEGABYTE) {
        put("a")
        put("b")
        put("c")
        assertNotNull(ImageBitmapCache["a"], "the fixture needs a to be present to read it")

        put("d")
        assertNotNull(ImageBitmapCache["a"], "a was read, so it was not the oldest")
        assertNull(ImageBitmapCache["b"], "b became the oldest and should have gone")
    }

    @Test
    fun `replacing a key does not count its bytes twice`() = withImageCacheBudget(3 * MEGABYTE) {
        put("a")
        put("b")
        put("c")
        val before = ImageBitmapCache.retainedBytes

        put("b")
        assertNotNull(ImageBitmapCache["a"], "a replacement evicted an entry it did not need to")
        assertEquals(before, ImageBitmapCache.retainedBytes, "the replaced bitmap was double-counted")
    }

    @Test
    fun `a bitmap larger than the whole budget is still admitted`() =
        withImageCacheBudget(3 * MEGABYTE) {
            put("small")
            put("huge", megabytes = 8)
            assertNotNull(ImageBitmapCache["huge"], "an image too big to cache became undrawable")
            assertNull(ImageBitmapCache["small"], "the cache kept something besides the newest")
            assertEquals(8 * MEGABYTE, ImageBitmapCache.retainedBytes)
        }

    @Test
    fun `paging through more images than fit never grows without bound`() =
        withImageCacheBudget(4 * MEGABYTE) {
            repeat(40) { index ->
                put("page-$index", megabytes = 2)
                assertTrue(
                    ImageBitmapCache.retainedBytes <= 4 * MEGABYTE,
                    "retained ${ImageBitmapCache.retainedBytes} bytes after ${index + 1} images"
                )
            }
            assertNotNull(ImageBitmapCache["page-39"], "the image on screen was evicted")
            assertNull(ImageBitmapCache["page-0"])
        }

    @Test
    fun `the shipped budget is the one the app runs with`() {
        assertEquals(256L * 1024 * 1024, ImageBitmapCache.budgetBytes)
    }

    /**
     * One file, every frame — what a save has to forget (ME-108 made frame sets
     * editable, and the recipe covers all of them).
     */
    @Test
    fun `removeAllFor drops every frame and preview of one path`() =
        withImageCacheBudget(8 * MEGABYTE) {
            val path = "D:/Photos/burst.heic"
            put(ImageBitmapCache.key(path, 0))
            put(ImageBitmapCache.key(path, 3))
            put(ImageBitmapCache.previewKey(path, 0))
            put(ImageBitmapCache.previewKey(path, 3))
            put(ImageBitmapCache.key("D:/Photos/burst.heic.backup", 0))

            ImageBitmapCache.removeAllFor(path)

            assertNull(ImageBitmapCache[ImageBitmapCache.key(path, 0)])
            assertNull(ImageBitmapCache[ImageBitmapCache.key(path, 3)])
            assertNull(ImageBitmapCache[ImageBitmapCache.previewKey(path, 0)])
            assertNull(ImageBitmapCache[ImageBitmapCache.previewKey(path, 3)])
            // A path this one is a prefix of is a different file, and stays.
            assertNotNull(ImageBitmapCache[ImageBitmapCache.key("D:/Photos/burst.heic.backup", 0)])
            assertEquals(MEGABYTE, ImageBitmapCache.retainedBytes)
        }
}
