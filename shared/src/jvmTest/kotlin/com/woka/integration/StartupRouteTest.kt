package com.woka.integration

import com.woka.app.navigation.Screen
import com.woka.app.navigation.startupStack
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `Woka.exe [path]` — the routing table in `roadmap.md` § Architecture & CLI
 * Routing, exercised as TC-SYS-01 walks it.
 *
 * Integration rather than unit: [startupStack] stats the argument, so every case
 * needs something real on disk. The dot in `File.exists()` is the whole point —
 * a pure version of this function would answer for paths that are not there.
 */
class StartupRouteTest {

    private fun <T> inDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("woka-startup").toFile()
        return try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun File.touch(name: String): File =
        File(this, name).also { it.writeText("x") }

    private fun folderOf(stack: List<Screen>): Screen.Folder = stack.last() as Screen.Folder

    @Test
    fun `no argument opens Home`() {
        assertEquals(listOf(Screen.Home), startupStack(emptyList()))
    }

    @Test
    fun `a blank argument opens Home`() {
        assertEquals(listOf(Screen.Home), startupStack(listOf("   ")))
    }

    @Test
    fun `a path that is not there opens Home`() {
        inDir { dir ->
            val gone = File(dir, "never-created.jpg").absolutePath
            assertEquals(listOf(Screen.Home), startupStack(listOf(gone)))
        }
    }

    @Test
    fun `a directory opens its Folder View and offers the pin`() {
        inDir { dir ->
            val folder = folderOf(startupStack(listOf(dir.absolutePath)))
            assertEquals(dir.absolutePath, folder.path)
            assertNull(folder.openViewerForId, "a directory must not open the viewer")
            assertTrue(folder.offerPin)
        }
    }

    @Test
    fun `a media file opens the viewer over its parent`() {
        inDir { dir ->
            val photo = dir.touch("shot.jpg")
            val folder = folderOf(startupStack(listOf(photo.absolutePath)))
            assertEquals(dir.absolutePath, folder.path)
            assertEquals(photo.absolutePath, folder.openViewerForId)
            assertTrue(folder.offerPin)
        }
    }

    @Test
    fun `a video is media too`() {
        inDir { dir ->
            val clip = dir.touch("clip.mkv")
            assertEquals(clip.absolutePath, folderOf(startupStack(listOf(clip.absolutePath))).openViewerForId)
        }
    }

    @Test
    fun `a file the app cannot show opens its parent instead`() {
        inDir { dir ->
            val notes = dir.touch("notes.txt")
            val folder = folderOf(startupStack(listOf(notes.absolutePath)))
            assertEquals(dir.absolutePath, folder.path)
            assertNull(folder.openViewerForId)
        }
    }

    @Test
    fun `an unquoted path with spaces is joined back together`() {
        inDir { dir ->
            val photo = dir.touch("a summer shot.jpg")
            val split = photo.absolutePath.split(" ")
            assertTrue(split.size > 1, "the fixture name has to contain a space")
            assertEquals(photo.absolutePath, folderOf(startupStack(split)).openViewerForId)
        }
    }

    @Test
    fun `Home stays underneath, so Back and the Home tab still work`() {
        inDir { dir ->
            val stack = startupStack(listOf(dir.absolutePath))
            assertEquals(2, stack.size)
            assertEquals(Screen.Home, stack.first())
        }
    }
}
