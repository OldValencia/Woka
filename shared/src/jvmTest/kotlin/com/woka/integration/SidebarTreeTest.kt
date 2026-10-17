package com.woka.integration

import com.woka.app.shell.sidebar.SidebarTreeState
import com.woka.app.shell.sidebar.ancestorsWithin
import com.woka.app.shell.sidebar.listSubfolders
import com.woka.app.shell.sidebar.pinnedParentLabel
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The sidebar's tree (SIDE-001): what a row lists, what it counts, and how the way
 * down to the open folder is found.
 *
 * Integration rather than unit because the listing and the count are questions
 * about a real directory — hidden is a file-system attribute on Windows, not a
 * leading dot, and only a real folder can carry it.
 */
class SidebarTreeTest {

    // Inline, so a `runTest` body can suspend inside it.
    private inline fun <T> inDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("woka-sidebar").toFile()
        return try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun File.dir(name: String): File = File(this, name).also { it.mkdirs() }
    private fun File.touch(name: String): File = File(this, name).also { it.writeText("x") }

    @Test
    fun `files and hidden folders are left out and the order ignores case`() {
        inDir { root ->
            root.dir("beta")
            root.dir("Alpha")
            root.dir("gamma")
            root.touch("notes.txt")
            val hidden = root.dir("secret")
            Files.setAttribute(hidden.toPath(), "dos:hidden", true)

            assertEquals(listOf("Alpha", "beta", "gamma"), listSubfolders(root.path).map { it.name })
        }
    }

    @Test
    fun `the count is media directly inside, never below`() {
        inDir { root ->
            val films = root.dir("Films")
            films.touch("a.mp4")
            films.touch("b.jpg")
            films.touch("readme.txt")
            films.dir("Extras").touch("c.mp4")

            val node = listSubfolders(root.path).single()
            assertEquals(2, node.mediaCount)
            assertTrue(node.hasChildren, "a folder with a visible sub-folder gets a chevron")
            assertFalse(listSubfolders(films.path).single().hasChildren)
        }
    }

    @Test
    fun `the way down matches across separators and stops at the root`() {
        val root = "D:\\Media\\Movies"
        val deep = "D:/Media/Movies/Series/Andor/Season 1/"

        val chain = ancestorsWithin(root, deep).map { File(it).name }
        assertEquals(listOf("Movies", "Series", "Andor"), chain)
        assertEquals(emptyList(), ancestorsWithin(root, "D:/Media/Moviesque/x"))
        assertEquals(emptyList(), ancestorsWithin(root, root))
    }

    @Test
    fun `a pin names the place it sits from its root down`() {
        val roots = listOf("D:/Media/Movies", "D:/Pictures")
        assertEquals(
            "Movies › Series › Andor",
            pinnedParentLabel("D:/Media/Movies/Series/Andor/Season 1", roots)
        )
        assertEquals("Pictures", pinnedParentLabel("D:/Pictures/Iceland", roots))
        assertEquals("Elsewhere", pinnedParentLabel("E:/Elsewhere/Folder", roots))
    }

    @Test
    fun `revealing a folder opens every row above it and lists them`() = runTest {
        inDir { root ->
            val andor = root.dir("Series").dir("Andor")
            andor.dir("Season 1")
            val tree = SidebarTreeState()

            tree.reveal(andor.path, listOf(root.path))

            val opened = tree.expanded.value.map { File(it).name }.toSet()
            assertEquals(setOf(root.name, "Series"), opened)
            val series = tree.children.value[File(root, "Series").path]
            assertEquals(listOf("Andor"), series?.map { it.name })
        }
    }
}
