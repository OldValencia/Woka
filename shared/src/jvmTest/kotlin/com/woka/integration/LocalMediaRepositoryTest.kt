package com.woka.integration

import com.woka.core.data.LocalMediaRepository
import com.woka.core.data.MediaItem
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun tempDir(): File = Files.createTempDirectory("woka-listing").toFile()

private fun File.touch(name: String): File =
    File(this, name).also { it.parentFile.mkdirs(); it.writeText("x") }

private fun items(dir: File): List<MediaItem> = runBlocking {
    LocalMediaRepository().getItems(dir.absolutePath)
}

class LocalMediaRepositoryTest {

    private fun <T> inDir(block: (File) -> T): T {
        val dir = tempDir()
        return try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `media is picked up and everything else is left out`() {
        inDir { dir ->
            dir.touch("photo.jpg")
            dir.touch("shot.png")
            dir.touch("clip.mp4")
            dir.touch("notes.txt")
            dir.touch("subs.srt")
            dir.touch("archive.zip")
            dir.touch("README")
            assertEquals(
                listOf("clip.mp4", "photo.jpg", "shot.png"),
                items(dir).map { it.name }
            )
        }
    }

    @Test
    fun `every extension MediaKind lists is actually returned`() {
        inDir { dir ->
            val names = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic")
                .plus(listOf("mp4", "mkv", "avi", "mov", "webm"))
                .map { "file.$it" }
            names.forEach { dir.touch(it) }
            assertEquals(names.size, items(dir).size, "a listed extension was dropped")
        }
    }

    @Test
    fun `subdirectories are listed as folders and not descended into`() {
        inDir { dir ->
            dir.touch("photo.jpg")
            dir.touch("Trip/inside.jpg")
            val listing = items(dir)
            assertTrue(listing[0] is MediaItem.Folder)
            assertEquals("Trip", listing[0].name)
            assertEquals(listOf("Trip", "photo.jpg"), listing.map { it.name })
        }
    }

    @Test
    fun `folders come first and both groups sort case-insensitively`() {
        inDir { dir ->
            listOf("beta.jpg", "Alpha.jpg", "delta.mp4").forEach { dir.touch(it) }
            listOf("zulu/x.jpg", "Charlie/x.jpg").forEach { dir.touch(it) }
            assertEquals(
                listOf("Charlie", "zulu", "Alpha.jpg", "beta.jpg", "delta.mp4"),
                items(dir).map { it.name }
            )
        }
    }

    @Test
    fun `the extension is matched whatever its case`() {
        inDir { dir ->
            listOf("A.JPG", "B.MkV", "C.WebP").forEach { dir.touch(it) }
            assertEquals(3, items(dir).size)
        }
    }

    @Test
    fun `an empty directory lists nothing`() {
        inDir { dir -> assertTrue(items(dir).isEmpty()) }
    }

    @Test
    fun `a directory of nothing but junk lists nothing`() {
        inDir { dir ->
            listOf("a.txt", "b.log", "c.srt").forEach { dir.touch(it) }
            assertTrue(items(dir).isEmpty())
        }
    }

    @Test
    fun `a path that does not exist gives an empty list, not an exception`() {
        val gone = File(tempDir(), "never-created")
        assertTrue(runBlocking { LocalMediaRepository().getItems(gone.absolutePath) }.isEmpty())
    }

    @Test
    fun `a file where a directory was expected gives an empty list`() {
        inDir { dir ->
            val file = dir.touch("photo.jpg")
            assertTrue(runBlocking { LocalMediaRepository().getItems(file.absolutePath) }.isEmpty())
        }
    }

    @Test
    fun `an item knows which kind it is`() {
        inDir { dir ->
            dir.touch("photo.jpg")
            dir.touch("clip.mkv")
            val byName = items(dir).associateBy { it.name }
            assertTrue(byName.getValue("photo.jpg") is MediaItem.Image)
            assertTrue(byName.getValue("clip.mkv") is MediaItem.Video)
        }
    }

    @Test
    fun `each item carries the stat it was listed with`() {
        inDir { dir ->
            val file = dir.touch("photo.jpg")
            file.setLastModified(1_600_000_000_000L)
            val item = items(dir).single()
            assertEquals(file.absolutePath, item.file.absolutePath)
            assertEquals(1_600_000_000_000L, item.lastModified)
        }
    }
}
