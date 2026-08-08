package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.core.data.MediaKind
import com.woka.core.data.needsWokaCover
import com.woka.core.data.isMedia
import com.woka.core.data.mediaItemOrNull
import com.woka.core.data.mediaKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Classification is by name only — nothing here opens a file. */
private fun named(name: String) = File("kind-fixture", name)

class MediaKindTest {

    @Test
    fun `every listed image extension classifies as an image`() {
        val listed =
            listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif", "tif", "tiff")
        listed.forEach { extension ->
            assertEquals(MediaKind.Image, named("photo.$extension").mediaKind(), extension)
        }
    }

    @Test
    fun `every listed video extension classifies as a video`() {
        listOf("mp4", "mkv", "avi", "mov", "webm").forEach { extension ->
            assertEquals(MediaKind.Video, named("clip.$extension").mediaKind(), extension)
        }
    }

    @Test
    fun `the extension is matched case-insensitively`() {
        assertEquals(MediaKind.Image, named("PHOTO.JPG").mediaKind())
        assertEquals(MediaKind.Video, named("Clip.MkV").mediaKind())
    }

    @Test
    fun `an unknown extension is not media`() {
        assertNull(named("notes.txt").mediaKind())
        assertNull(named("archive.zip").mediaKind())
        assertNull(named("subtitles.srt").mediaKind())
        assertFalse(named("notes.txt").isMedia)
    }

    @Test
    fun `a file with no extension is not media`() {
        assertNull(named("README").mediaKind())
        assertFalse(named("README").isMedia)
    }

    @Test
    fun `a dotfile is judged by what follows the last dot`() {
        assertNull(named(".gitignore").mediaKind())
        assertEquals(MediaKind.Image, named(".hidden.png").mediaKind())
    }

    @Test
    fun `only the last extension counts`() {
        assertEquals(MediaKind.Video, named("WokaSample.S01E01.2160p.mkv").mediaKind())
        assertNull(named("clip.mp4.part").mediaKind())
    }

    @Test
    fun `isMedia agrees with mediaKind`() {
        listOf("photo.png", "clip.mkv", "notes.txt", "README").forEach { name ->
            val file = named(name)
            assertEquals(file.mediaKind() != null, file.isMedia, name)
        }
    }

    /**
     * Coil reads neither HEIF, AVIF nor TIFF, so the grid draws those five with
     * Woka's own decoders. `.gif` and `.webp` are absent because Coil does read
     * them, animation included — routing those away would lose the animation.
     */
    @Test
    fun `the formats Coil cannot read take the grid's other road`() {
        listOf("heic", "heif", "avif", "tif", "tiff").forEach { extension ->
            val file = named("photo.$extension")
            assertEquals(MediaKind.Image, file.mediaKind(), extension)
            assertTrue(file.needsWokaCover, extension)
        }
        listOf("png", "jpg", "gif", "webp", "bmp").forEach {
            assertFalse(named("photo.$it").needsWokaCover, it)
        }
    }

    @Test
    fun `the item factory picks the matching subclass`() {
        assertTrue(mediaItemOrNull(named("photo.png")) is MediaItem.Image)
        assertTrue(mediaItemOrNull(named("clip.mkv")) is MediaItem.Video)
        assertNull(mediaItemOrNull(named("notes.txt")))
    }
}
