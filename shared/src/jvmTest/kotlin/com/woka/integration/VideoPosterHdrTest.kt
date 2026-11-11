package com.woka.integration

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.woka.core.data.HdrView
import com.woka.core.data.HdrViewMode
import com.woka.core.data.MediaItem
import com.woka.core.data.WokaStorage
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.feature.viewer.video.engine.VideoThumbnailExtractor
import com.woka.support.posterDifference
import com.woka.support.posterReference
import com.woka.support.withTempStorageRoot
import com.woka.support.writePosterFixture
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VideoPosterHdrTest {
    @Test
    fun `a rejected fallback keeps its pixels when later monochrome candidates are decoded`() = withTempStorageRoot { root ->
        HdrView.install { HdrViewMode.Auto }
        val file = File(root, "fallback.mkv").also { writePosterFixture(it, "smpte2084", flatFallback = true) }
        val expected = posterReference(file, HdrViewMode.Auto, "PQ")
        try {
            val actual = assertNotNull(VideoThumbnailExtractor.load(MediaItem.Video(file)))
            assertTrue(posterDifference(expected, actual) <= 1f, "later pixels replaced the retained fallback")
        } finally {
            expected.asSkiaBitmap().close()
            ImageBitmapCache.removeAllFor(file.absolutePath)
        }
    }

    @Test
    fun `PQ and HLG posters match the viewer and SDR stays unchanged`() = withTempStorageRoot { root ->
        HdrView.install { HdrViewMode.Auto }
        for ((tag, transfer) in listOf("smpte2084" to "PQ", "arib-std-b67" to "HLG", "bt709" to "SDR")) {
            val file = File(root, "$tag.mkv").also { writePosterFixture(it, tag) }
            val expected = posterReference(file, HdrViewMode.Auto, transfer)
            try {
                val actual = assertNotNull(VideoThumbnailExtractor.load(MediaItem.Video(file)))
                assertTrue(posterDifference(expected, actual) <= 1f, "$transfer differs from the viewer")
            } finally {
                expected.asSkiaBitmap().close()
                ImageBitmapCache.removeAllFor(file.absolutePath)
            }
        }
    }

    @Test
    fun `HDR modes have separate memory and disk posters and switching back hits Auto`() = withTempStorageRoot { root ->
        var mode = HdrViewMode.Auto
        HdrView.install { mode }
        val file = File(root, "mode.mkv").also { writePosterFixture(it, "smpte2084") }
        val item = MediaItem.Video(file)
        try {
            val auto = assertNotNull(runBlocking { MediaThumbnails.loadThumbnail(item) })
            val autoFile = WokaStorage.thumbnailFile(file)
            mode = HdrViewMode.ForceSdr
            val off = assertNotNull(runBlocking { MediaThumbnails.loadThumbnail(item) })
            assertNotEquals(autoFile, WokaStorage.thumbnailFile(file))
            assertTrue(autoFile.isFile && WokaStorage.thumbnailFile(file).isFile)
            assertSame(off, ImageBitmapCache[ImageBitmapCache.key(file.absolutePath, 0)])
            assertTrue(posterDifference(auto, off) > 10f, "Off still serves mapped pixels")
            mode = HdrViewMode.Auto
            assertSame(auto, runBlocking { MediaThumbnails.loadThumbnail(item) })
        } finally {
            for (value in HdrViewMode.entries) {
                mode = value
                ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))
            }
            HdrView.install { HdrViewMode.Auto }
        }
    }

    @Test
    fun `On also maps an SDR tagged video before caching`() = withTempStorageRoot { root ->
        val file = File(root, "forced.mkv").also { writePosterFixture(it, "bt709") }
        HdrView.install { HdrViewMode.ForceHdr }
        val expected = posterReference(file, HdrViewMode.ForceHdr, "SDR")
        try {
            val actual = assertNotNull(VideoThumbnailExtractor.load(MediaItem.Video(file)))
            assertTrue(posterDifference(expected, actual) <= 1f)
        } finally {
            ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))
            expected.asSkiaBitmap().close()
            HdrView.install { HdrViewMode.Auto }
        }
    }

    @Test
    fun `a cached HDR poster is read without a second tone map`() = withTempStorageRoot { root ->
        HdrView.install { HdrViewMode.Auto }
        val file = File(root, "cached.mkv").also { writePosterFixture(it, "smpte2084") }
        val item = MediaItem.Video(file)
        try {
            val first = assertNotNull(VideoThumbnailExtractor.load(item))
            val cacheFile = WokaStorage.thumbnailFile(file)
            val stored = Image.makeFromEncoded(cacheFile.readBytes()).use { it.toComposeImageBitmap() }
            try {
                assertTrue(posterDifference(first, stored) <= 12f, "WebP does not contain the mapped picture")
                val loaded = assertNotNull(VideoThumbnailExtractor.load(item))
                assertEquals(0f, posterDifference(stored, loaded), "the disk reader mapped the image again")
            } finally {
                stored.asSkiaBitmap().close()
            }
        } finally {
            ImageBitmapCache.removeAllFor(file.absolutePath)
        }
    }

    @Test
    fun `v5 unmapped posters are discarded once and v6 entries survive`() = withTempStorageRoot { root ->
        val dir = File(root, ".thumbnails").also { it.mkdirs() }
        File(dir, ".keys-v5").writeText("")
        val old = File(dir, "unmapped.webp").also { it.writeText("old") }
        assertEquals(dir, WokaStorage.thumbnailsDir)
        assertTrue(!old.exists())
        assertTrue(File(dir, ".keys-v6").isFile)
        val current = File(dir, "mapped.webp").also { it.writeText("current") }
        assertEquals(dir, WokaStorage.thumbnailsDir)
        assertTrue(current.isFile)
    }
}
