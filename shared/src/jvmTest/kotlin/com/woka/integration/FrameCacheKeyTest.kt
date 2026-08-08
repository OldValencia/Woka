package com.woka.integration

import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.ImageLoader
import com.woka.fixtures.ANIM_FRAME_COUNT
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.SampleMedia
import com.woka.support.MEGABYTE
import com.woka.support.bitmapOf
import com.woka.support.withImageCacheBudget
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** A private copy, so an entry another test left cannot answer for it. */
private fun copyOf(name: String, as_: String): File {
    val dir = Files.createTempDirectory("woka-frame-cache").toFile()
    val target = File(dir, as_)
    SampleMedia.file(name).copyTo(target)
    return target
}

/**
 * One file, many pictures, one cache.
 *
 * Keyed by absolute path alone — which is what it was — a twelve-frame burst is
 * twelve collisions on one key: every frame overwrites the last and the strip
 * shows whichever decoded most recently.
 */
class FrameCacheKeyTest {

    @Test
    fun `frame zero keeps the bare path`() {
        assertEquals("/photos/a.heic", ImageBitmapCache.key("/photos/a.heic", 0))
    }

    /**
     * Frame 0 keeping the bare path is not cosmetic: every caller that predates
     * frame sets reads and writes that key, and changing it would have made all
     * of them miss.
     */
    @Test
    fun `later frames get keys of their own and never collide`() {
        val keys = (0 until 12).map { ImageBitmapCache.key("/photos/a.heic", it) }
        assertEquals(keys.size, keys.distinct().size, "two frames share a key")
        assertTrue(keys.drop(1).none { it == "/photos/a.heic" })
    }

    /** A preview is a different picture of the same frame, so a different key. */
    @Test
    fun `a preview never shares a key with the full picture`() {
        val path = "/photos/a.heic"
        assertNotSame(ImageBitmapCache.key(path, 0), ImageBitmapCache.previewKey(path, 0))
        assertTrue(ImageBitmapCache.key(path, 3) != ImageBitmapCache.previewKey(path, 3))
        val previews = (0 until 5).map { ImageBitmapCache.previewKey(path, it) }
        assertEquals(previews.size, previews.distinct().size)
    }

    @Test
    fun `decoding each frame of an animation caches them separately`() {
        val file = copyOf(ANIM_GIF, "keys.gif")
        (0 until ANIM_FRAME_COUNT).forEach { ImageLoader.load(file, it) }
        (0 until ANIM_FRAME_COUNT).forEach { frame ->
            val key = ImageBitmapCache.key(file.absolutePath, frame)
            assertNotNull(ImageBitmapCache[key], "frame $frame was not cached under its own key")
        }
    }

    /**
     * Paging a burst end to end must not grow without bound. Driven at a small
     * budget so the arithmetic is legible — the shipped 256 MB does the same
     * thing three orders of magnitude further out.
     */
    @Test
    fun `a burst paged end to end stays inside the budget`() = withImageCacheBudget(4 * MEGABYTE) {
        repeat(20) { frame ->
            ImageBitmapCache[ImageBitmapCache.key("/photos/burst.heic", frame)] = bitmapOf(1)
        }
        assertTrue(
            ImageBitmapCache.retainedBytes <= 4 * MEGABYTE,
            "20 frames of 1 MB retained ${ImageBitmapCache.retainedBytes} against a 4 MB budget"
        )
    }
}
