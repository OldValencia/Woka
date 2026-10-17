package com.woka.integration

import com.woka.feature.search.searchLibrary
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The search behind the toolbar's box (SIDE-008), on a real temp tree: deep, case
 * blind, media and folders only, hidden folders unentered, and stoppable.
 */
class LibrarySearchTest {

    private inline fun <T> inDir(block: (File) -> T): T {
        val dir = Files.createTempDirectory("woka-search").toFile()
        return try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun File.touch(path: String): File = File(this, path).also {
        it.parentFile.mkdirs()
        it.writeText("x")
    }

    @Test
    fun `a photo deep in the tree is found, ignoring case`() = inDir { root ->
        root.touch("Pictures/Iceland 2025/Ice_Cave_02.jpg")
        root.touch("Pictures/Iceland 2025/notes.txt")
        val hits = searchLibrary("ice", listOf(root.path), listOf(root.path)) { true }
        val names = hits.map { it.file.name }.toSet()
        assertEquals(setOf("Iceland 2025", "Ice_Cave_02.jpg"), names)
    }

    @Test
    fun `a hit names the place it sits from its root`() = inDir { root ->
        root.touch("Pictures/Iceland 2025/Ice_Cave_02.jpg")
        val hit = searchLibrary("cave", listOf(root.path), listOf(root.path)) { true }.single()
        assertEquals("${root.name} › Pictures › Iceland 2025", hit.place)
    }

    /** The grid threw on the duplicate key when `Heic` was linked inside `Pictures` (2026-10-17). */
    @Test
    fun `a file under two nested linked roots is one result`() = inDir { root ->
        root.touch("Pictures/Heic/Tech Factory.heic")
        val roots = listOf(File(root, "Pictures").path, File(root, "Pictures/Heic").path)
        val hits = searchLibrary("te", roots, roots) { true }
        assertEquals(1, hits.count { it.file.name == "Tech Factory.heic" })
    }

    @Test
    fun `a hidden folder is not entered`() = inDir { root ->
        val hidden = File(root, "secret").also { it.mkdirs() }
        Files.setAttribute(hidden.toPath(), "dos:hidden", true)
        root.touch("secret/ice.jpg")
        assertTrue(searchLibrary("ice", listOf(root.path), listOf(root.path)) { true }.isEmpty())
    }

    @Test
    fun `a search no longer wanted stops`() = inDir { root ->
        root.touch("a/ice.jpg")
        assertTrue(searchLibrary("ice", listOf(root.path), listOf(root.path)) { false }.isEmpty())
    }
}
