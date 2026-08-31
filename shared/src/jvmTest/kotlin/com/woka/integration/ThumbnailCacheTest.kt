package com.woka.integration

import com.woka.core.data.MediaItem
import com.woka.core.data.WokaStorage
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.feature.viewer.video.engine.VideoThumbnailExtractor
import com.woka.fixtures.SampleMedia
import com.woka.support.videoCopy
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun poster(item: MediaItem.Video) = runBlocking { MediaThumbnails.loadThumbnail(item) }

private fun episodeTwo() = SampleMedia.episode(2, ".mkv")

/** A size no frame of a 16:9 fixture scaled to 400 px can have, so it identifies itself. */
private const val PLANTED_POSTER_PX = 37

/**
 * A poster to leave in the cache directory and recognise on the way back out.
 *
 * PNG, not WebP, although entries are WebP since the v3 schema: Skia decodes from the
 * container's own bytes and never consults the name, so writing the simpler format
 * here tests the reader rather than the fixture. [flat] makes every pixel one colour,
 * which is exactly what `isMonochrome` rejects.
 */
private fun plantedPoster(flat: Boolean = false): ByteArray {
    val image = BufferedImage(PLANTED_POSTER_PX, PLANTED_POSTER_PX, BufferedImage.TYPE_INT_RGB)
    if (!flat) {
        for (y in 0 until PLANTED_POSTER_PX) {
            for (x in 0 until PLANTED_POSTER_PX) image.setRGB(x, y, x * 7 + y * 2_000)
        }
    }
    val stream = ByteArrayOutputStream()
    ImageIO.write(image, "png", stream)
    return stream.toByteArray()
}

/**
 * The two caches a poster passes through: `ImageBitmapCache` and the PNG on disk.
 * The in-memory one's eviction policy is `ImageCacheEvictionTest` (F1).
 */
class ThumbnailCacheTest {

    @Test
    fun `a second request is answered from the in-memory cache`() {
        withTempStorageRoot {
            val item = videoCopy(episodeTwo(), "memcache.mkv")
            val first = assertNotNull(poster(item))
            assertSame(first, ImageBitmapCache[item.file.absolutePath])
            assertSame(first, poster(item), "the second call decoded again")
        }
    }

    /**
     * **This test used to pass while the cache was never hit at all**, by comparing the
     * poster's bytes: every fixture is low-motion footage, so a re-extraction wrote the
     * same pixels and the comparison held either way (`ThumbnailCacheProbe`, 2026-08-31).
     * mtime is no signal either, now that a hit calls `markUsed` and moves it on purpose.
     * A poster planted at a size no frame of the video has settles it: served back, it
     * was read; replaced, it was rebuilt.
     *
     * Called straight on the extractor, because `MediaThumbnails` would answer from the
     * in-memory cache and never reach the disk path at all.
     */
    @Test
    fun `an existing cache file is read back instead of decoding again`() {
        withTempStorageRoot {
            val item = videoCopy(episodeTwo(), "diskcache.mkv")
            assertNotNull(VideoThumbnailExtractor.load(item))
            WokaStorage.thumbnailFile(item.file).writeBytes(plantedPoster())

            val served = assertNotNull(VideoThumbnailExtractor.load(item))

            assertEquals(PLANTED_POSTER_PX, served.width, "the poster was re-extracted")
        }
    }

    /**
     * The case the rewrite was hiding: a poster `isMonochrome` rejects is still a
     * poster. A flat opening frame is an ordinary thing for a video to have, and it
     * used to send every launch back through ffmpeg, forever.
     */
    @Test
    fun `a poster the heuristic would reject is still served from disk`() {
        withTempStorageRoot {
            val item = videoCopy(episodeTwo(), "flat.mkv")
            assertNotNull(VideoThumbnailExtractor.load(item))
            WokaStorage.thumbnailFile(item.file).writeBytes(plantedPoster(flat = true))

            val served = assertNotNull(VideoThumbnailExtractor.load(item))

            assertEquals(PLANTED_POSTER_PX, served.width, "a flat poster was re-extracted")
        }
    }

    /**
     * What the sweep's ordering is built on: reading an entry has to leave a mark, or
     * eviction by mtime is FIFO and "delete what has not been looked at" is not
     * expressible. Asserted here rather than in `ThumbnailSweepTest` because this is
     * the only place the mark is actually *produced*.
     */
    @Test
    fun `serving an entry from disk marks it as used`() {
        withTempStorageRoot {
            val item = videoCopy(episodeTwo(), "marked.mkv")
            assertNotNull(VideoThumbnailExtractor.load(item))
            val cacheFile = WokaStorage.thumbnailFile(item.file)
            val backdated = cacheFile.lastModified() - 90_000L
            assertTrue(cacheFile.setLastModified(backdated), "the fixture could not be backdated")

            assertNotNull(VideoThumbnailExtractor.load(item))

            assertTrue(cacheFile.lastModified() > backdated, "a disk hit left no mark")
        }
    }

    @Test
    fun `two videos of the same name in different folders get their own poster`() {
        withTempStorageRoot {
            val first = videoCopy(episodeTwo(), "same.mkv")
            val second = videoCopy(episodeTwo(), "same.mkv")
            assertNotNull(poster(first))
            assertNotNull(poster(second))
            val firstCache = WokaStorage.thumbnailFile(first.file)
            val secondCache = WokaStorage.thumbnailFile(second.file)
            assertTrue(firstCache != secondCache, "the two files shared one cache entry")
            assertTrue(firstCache.isFile && secondCache.isFile)
        }
    }

    @Test
    fun `the cache directory carries its schema marker`() {
        withTempStorageRoot {
            val item = videoCopy(episodeTwo(), "marker.mkv")
            assertNotNull(poster(item))
            assertTrue(WokaStorage.thumbnailsDir.isDirectory)
        }
    }
}
