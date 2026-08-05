package com.woka.integration

import com.woka.core.data.MediaItem
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.fixtures.EXIF_EXPECTED_HEIGHT
import com.woka.fixtures.EXIF_EXPECTED_WIDTH
import com.woka.fixtures.EXIF_ROTATED
import com.woka.fixtures.STILL_HEIGHT
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.STILL_WEBP
import com.woka.fixtures.STILL_WIDTH
import com.woka.fixtures.SampleMedia
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A private copy, so a cache entry from another test cannot answer for it. */
private fun copyOf(name: String, as_: String): File {
    val dir = Files.createTempDirectory("woka-image").toFile()
    val target = File(dir, as_)
    SampleMedia.file(name).copyTo(target)
    return target
}

class ImageLoaderTest {

    @Test
    fun `every still format decodes at its native size`() {
        listOf(STILL_PNG, STILL_JPG, STILL_WEBP).forEach { name ->
            val bitmap = assertNotNull(ImageLoader.load(SampleMedia.file(name)), name)
            assertEquals(STILL_WIDTH, bitmap.width, name)
            assertEquals(STILL_HEIGHT, bitmap.height, name)
        }
    }

    @Test
    fun `a decoded still is put in the shared cache under its path`() {
        val file = copyOf(STILL_PNG, "cached.png")
        val bitmap = assertNotNull(ImageLoader.load(file))
        assertSame(bitmap, ImageBitmapCache[file.absolutePath])
    }

    @Test
    fun `a format the app lists but cannot decode returns null without being read`() {
        val pretendHeif = copyOf(STILL_PNG, "photo.heic")
        assertNull(ImageLoader.load(pretendHeif))
    }

    @Test
    fun `a corrupt file fails inside the loader and is absorbed by the facade`() {
        val corrupt = copyOf("broken.mkv", "corrupt.png")
        // The loader itself does not guard `makeFromEncoded`; MediaThumbnails is
        // where every call goes and where the failure is turned into a placeholder.
        assertFails { ImageLoader.load(corrupt) }
        assertNull(runBlocking { MediaThumbnails.loadThumbnail(MediaItem.Image(corrupt)) })
    }

    @Test
    fun `a file that is not there is absorbed by the facade too`() {
        val missing = File(Files.createTempDirectory("woka-image").toFile(), "gone.png")
        assertFails { ImageLoader.load(missing) }
        assertNull(runBlocking { MediaThumbnails.loadThumbnail(MediaItem.Image(missing)) })
    }

    @Test
    fun `an empty file does not come back as a zero-sized bitmap`() {
        val empty = File(Files.createTempDirectory("woka-image").toFile(), "empty.png")
        empty.writeBytes(ByteArray(0))
        assertNull(runBlocking { MediaThumbnails.loadThumbnail(MediaItem.Image(empty)) })
    }

    @Test
    fun `the facade returns the same bitmap the loader cached`() {
        val file = copyOf(STILL_JPG, "facade.jpg")
        val first = assertNotNull(runBlocking { MediaThumbnails.loadThumbnail(MediaItem.Image(file)) })
        val second = assertNotNull(runBlocking { MediaThumbnails.loadThumbnail(MediaItem.Image(file)) })
        assertSame(first, second, "the second call decoded again instead of using the cache")
    }

    @Test
    fun `the same picture under two paths is decoded twice`() {
        val one = copyOf(STILL_PNG, "one.png")
        val two = copyOf(STILL_PNG, "two.png")
        val first = assertNotNull(ImageLoader.load(one))
        val second = assertNotNull(ImageLoader.load(two))
        assertEquals(first.width, second.width)
        assertNotNull(ImageBitmapCache[one.absolutePath])
        assertNotNull(ImageBitmapCache[two.absolutePath])
    }

    @Test
    @Ignore(
        "F3: ImageLoader goes straight to Image.makeFromEncoded, which ignores the " +
            "EXIF Orientation tag, so a camera photo shot in portrait opens on its " +
            "side. Fix in Phase 7, then drop this annotation."
    )
    fun `an EXIF-rotated photo comes back the way round it was taken`() {
        val bitmap = assertNotNull(ImageLoader.load(SampleMedia.file(EXIF_ROTATED)))
        assertEquals(EXIF_EXPECTED_WIDTH, bitmap.width)
        assertEquals(EXIF_EXPECTED_HEIGHT, bitmap.height)
    }

    @Test
    fun `the EXIF fixture is a landscape photo carrying a portrait tag`() {
        // Guards the fixture, not the loader, and through ImageIO rather than
        // ImageLoader so it stays true once F3 is fixed: on a square source the
        // check above would pass without the tag ever being read.
        val raw = assertNotNull(ImageIO.read(SampleMedia.file(EXIF_ROTATED)))
        assertEquals(STILL_WIDTH, raw.width, "the fixture stopped being landscape")
        assertEquals(STILL_HEIGHT, raw.height)
        assertNotEquals(raw.width, raw.height, "a square fixture cannot detect a rotation")
    }
}
