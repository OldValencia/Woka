package com.woka.integration

import com.woka.app.navigation.Screen
import com.woka.app.navigation.startupStack
import com.woka.core.data.PinnedSession
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a bare launch reopens once Home is gone (SIDE-002): the folder the last
 * session left off in, while it still exists inside a linked root; otherwise the
 * first linked root that exists; and an argument always wins over both.
 *
 * Integration for the same reason as `StartupRouteTest`: every row is a question
 * about what is on disk now.
 */
class StartupRestoreTest {

    private fun <T> inDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("woka-restore").toFile()
        return try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun folderOf(stack: List<Screen>): Screen.Folder = stack.last() as Screen.Folder

    @Test
    fun `no argument reopens the last folder when it sits inside a linked root`() {
        inDir { root ->
            val deep = File(root, "Series/Andor").also { it.mkdirs() }
            val last = PinnedSession(deep.absolutePath, scrollIndex = 40, scrollOffset = 12)
            val stack = startupStack(emptyList(), last, listOf(root.absolutePath))
            assertEquals(listOf<Screen>(Screen.Folder(deep.absolutePath)), stack)
        }
    }

    @Test
    fun `a last folder outside every root falls to the first root`() {
        inDir { dir ->
            val root = File(dir, "Linked").also { it.mkdirs() }
            val stray = File(dir, "Elsewhere").also { it.mkdirs() }
            val last = PinnedSession(stray.absolutePath)
            val stack = startupStack(emptyList(), last, listOf(root.absolutePath))
            assertEquals(root.absolutePath, folderOf(stack).path)
        }
    }

    @Test
    fun `a last folder that was deleted falls to the first root that exists`() {
        inDir { dir ->
            val root = File(dir, "Linked").also { it.mkdirs() }
            val gone = File(root, "Removed").absolutePath
            val linked = listOf(File(dir, "Unplugged").absolutePath, root.absolutePath)
            val stack = startupStack(emptyList(), PinnedSession(gone), linked)
            assertEquals(root.absolutePath, folderOf(stack).path)
        }
    }

    @Test
    fun `an argument wins over the last folder`() {
        inDir { dir ->
            val root = File(dir, "Linked").also { it.mkdirs() }
            val other = File(dir, "Other").also { it.mkdirs() }
            val last = PinnedSession(root.absolutePath)
            val stack = startupStack(listOf(other.absolutePath), last, listOf(root.absolutePath))
            assertEquals(other.absolutePath, folderOf(stack).path)
            assertTrue(folderOf(stack).offerPin)
        }
    }
}
