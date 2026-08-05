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
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

private fun poster(item: MediaItem.Video) = runBlocking { MediaThumbnails.loadThumbnail(item) }

private fun episodeTwo() = SampleMedia.episode(2, ".mkv")

/** The two caches a poster passes through: `ImageBitmapCache` and the PNG on disk. */
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

    @Test
    fun `an existing cache file is read back instead of decoding again`() {
        withTempStorageRoot {
            val item = videoCopy(episodeTwo(), "diskcache.mkv")
            assertNotNull(VideoThumbnailExtractor.load(item))
            val cacheFile = WokaStorage.thumbnailFile(item.file)
            val written = cacheFile.readBytes()

            // Called straight on the extractor: MediaThumbnails would answer from
            // the in-memory cache and never reach the disk path at all. The
            // candidate timestamps are randomised, so a re-extraction would almost
            // certainly write different pixels — comparing the bytes is the check.
            assertNotNull(VideoThumbnailExtractor.load(item))
            assertTrue(written.contentEquals(cacheFile.readBytes()), "the poster was re-extracted")
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

    @Test
    @Ignore(
        "F1: ImageBitmapCache is a ConcurrentHashMap with no eviction, so every " +
            "full-size bitmap opened stays in RAM for the life of the process. The " +
            "policy is Valik's decision (Phase 7); this check is written once it is " +
            "made, and needs the measurement recorded in TC-PERF."
    )
    fun `loading many large images does not grow retained memory without bound`() {
        fail("written once the eviction policy exists; there is nothing to assert against yet")
    }
}
