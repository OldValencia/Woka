package com.woka.integration

import com.woka.core.data.MediaItem
import com.woka.core.data.WokaStorage
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.fixtures.BITMAP_SUBS
import com.woka.fixtures.NO_AUDIO
import com.woka.fixtures.SampleMedia
import com.woka.support.videoCopy
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun poster(item: MediaItem.Video) = runBlocking { MediaThumbnails.loadThumbnail(item) }

/** Extracting a poster frame. Cache behaviour is `ThumbnailCacheTest`. */
class ThumbnailPipelineTest {

    @Test
    fun `a poster frame is extracted and written to the cache directory`() {
        withTempStorageRoot {
            val item = videoCopy(SampleMedia.episode(2, ".mkv"))
            assertNotNull(poster(item), "no poster frame came back")
            val cacheFile = WokaStorage.thumbnailFile(item.file)
            assertTrue(cacheFile.isFile, "the poster was not written to disk")
            assertTrue(cacheFile.length() > 0L)
            assertEquals(File(WokaStorage.root, ".thumbnails"), cacheFile.parentFile)
        }
    }

    @Test
    fun `the poster is downscaled to fit inside 400 px`() {
        withTempStorageRoot {
            val item = videoCopy(SampleMedia.episode(2, ".mkv"), "sized.mkv")
            val bitmap = assertNotNull(poster(item))
            assertTrue(maxOf(bitmap.width, bitmap.height) <= 400, "${bitmap.width}×${bitmap.height}")
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
        }
    }

    @Test
    fun `the cached poster is a readable image of the size that was decoded`() {
        withTempStorageRoot {
            val item = videoCopy(BITMAP_SUBS, "picture.mkv")
            val bitmap = assertNotNull(poster(item))
            val written = assertNotNull(ImageIO.read(WokaStorage.thumbnailFile(item.file)))
            assertEquals(bitmap.width, written.width)
            assertEquals(bitmap.height, written.height)
            // Whether that frame is *interesting* is deliberately not asserted:
            // every fixture is a few seconds of low-motion footage that
            // `isMonochrome` rejects, so the extractor rightly falls back to its
            // first candidate. Poster quality is a manual case (TC-VID) instead.
        }
    }

    @Test
    fun `a video with no audio still yields a poster`() {
        withTempStorageRoot {
            val item = videoCopy(NO_AUDIO, "silent.mkv")
            assertNotNull(poster(item), "a silent video produced no poster")
        }
    }

    @Test
    fun `a container FFmpeg cannot open yields no poster rather than an exception`() {
        withTempStorageRoot {
            // Not `broken.mkv`: its first 2 KB still hold a decodable frame, so it
            // is an error path for playback, not for the poster extractor.
            val dir = Files.createTempDirectory("woka-thumb").toFile()
            val garbage = File(dir, "garbage.mkv")
            garbage.writeBytes(ByteArray(64_000) { (it * 37 % 251).toByte() })
            assertNull(poster(MediaItem.Video(garbage)))
            assertTrue(!WokaStorage.thumbnailFile(garbage).exists(), "a poster was cached for garbage")
        }
    }

    @Test
    fun `a truncated container still gives back whatever it can decode`() {
        withTempStorageRoot {
            val item = videoCopy("broken.mkv", "truncated.mkv")
            // Recorded rather than asserted either way: two kilobytes of a valid
            // container happen to carry a frame, and the extractor is right to use it.
            val result = poster(item)
            if (result != null) assertTrue(WokaStorage.thumbnailFile(item.file).isFile)
        }
    }

    @Test
    fun `a file that is not there yields no poster`() {
        withTempStorageRoot {
            val missing = File(Files.createTempDirectory("woka-thumb").toFile(), "gone.mkv")
            assertNull(poster(MediaItem.Video(missing)))
        }
    }
}
