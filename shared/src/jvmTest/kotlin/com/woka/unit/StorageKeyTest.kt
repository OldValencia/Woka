package com.woka.unit

import com.woka.core.data.WokaStorage
import com.woka.support.withTempStorageRoot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The key reads `length()` and `lastModified()` and nothing else — the source is
 * never opened. The files below exist only to carry those two numbers, and they
 * live under [withTempStorageRoot] so no real cache is touched.
 */
private fun source(dir: File, name: String, bytes: String = "x"): File {
    val file = File(dir, name)
    file.parentFile.mkdirs()
    file.writeText(bytes)
    return file
}

class StorageKeyTest {

    @Test
    fun `the same file always gets the same name`() {
        withTempStorageRoot { root ->
            val file = source(root, "media/clip.mp4")
            val first = WokaStorage.thumbnailFile(file)
            repeat(5) { assertEquals(first, WokaStorage.thumbnailFile(file)) }
            assertEquals(first, WokaStorage.thumbnailFile(File(file.absolutePath)))
        }
    }

    @Test
    fun `same-named files in different folders do not share a thumbnail`() {
        withTempStorageRoot { root ->
            val first = source(root, "a/clip.mp4")
            val second = source(root, "b/clip.mp4")
            assertNotEquals(WokaStorage.thumbnailFile(first), WokaStorage.thumbnailFile(second))
        }
    }

    @Test
    fun `a file replaced in place gets a new key`() {
        withTempStorageRoot { root ->
            val file = source(root, "clip.mp4", "small")
            val before = WokaStorage.thumbnailFile(file)
            file.writeText("a decidedly larger payload")
            assertNotEquals(before, WokaStorage.thumbnailFile(file))
        }
    }

    @Test
    fun `a touched file gets a new key even at the same size`() {
        withTempStorageRoot { root ->
            val file = source(root, "clip.mp4", "same-size")
            val before = WokaStorage.thumbnailFile(file)
            val touched = file.setLastModified(file.lastModified() + 120_000L)
            assertTrue(touched, "the fixture could not be touched, so nothing was tested")
            assertNotEquals(before, WokaStorage.thumbnailFile(file))
        }
    }

    @Test
    fun `a non-ASCII path produces a usable file name`() {
        withTempStorageRoot { root ->
            val file = source(root, "Отпуск 2024/клип №1 — копия.mp4")
            val thumbnail = WokaStorage.thumbnailFile(file)
            assertTrue(thumbnail.name.matches(Regex("[0-9a-f]{16}\\.webp")), thumbnail.name)
        }
    }

    @Test
    fun `every key is positive hex of a fixed width`() {
        withTempStorageRoot { root ->
            val names = (1..20).map { WokaStorage.thumbnailFile(source(root, "clip$it.mp4")).name }
            names.forEach { assertTrue(it.matches(Regex("[0-9a-f]{16}\\.webp")), it) }
            assertEquals(20, names.toSet().size, "two of twenty files collided")
        }
    }

    @Test
    fun `thumbnails live under the storage root, so a test cannot reach the real cache`() {
        withTempStorageRoot { root ->
            val thumbnail = WokaStorage.thumbnailFile(source(root, "clip.mp4"))
            assertEquals(File(root, ".thumbnails"), thumbnail.parentFile)
            assertEquals(File(root, "settings.json"), WokaStorage.settingsFile)
            assertEquals(root, WokaStorage.root)
        }
    }

    @Test
    fun `the cache directory is prepared and marked on first use`() {
        withTempStorageRoot { _ ->
            val dir = WokaStorage.thumbnailsDir
            assertTrue(dir.isDirectory, "the cache directory was not created")
            assertTrue(File(dir, ".keys-v4").isFile, "the schema marker was not written")
        }
    }

    @Test
    fun `pointing the root somewhere else moves the cache with it`() {
        val first = withTempStorageRoot { root ->
            WokaStorage.thumbnailsDir
            root
        }
        withTempStorageRoot { root ->
            assertNotEquals(first, root)
            assertEquals(File(root, ".thumbnails"), WokaStorage.thumbnailsDir)
        }
    }
}
