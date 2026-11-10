package com.woka.integration

import com.woka.feature.folder.FolderActions
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A copy appears under its name only when whole (reported 2026-11-10): a 4 GB `mkv`
 * duplicated and opened at once was read while still short. What is checkable here is the
 * end state — the bytes, and no `.woka-tmp` left behind — for a file and for a folder.
 */
class FolderWholeCopyTest {

    private val dir: File = Files.createTempDirectory("woka-whole-copy").toFile()

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    private fun film(parent: File = dir, name: String = "film.mkv"): File =
        File(parent, name).apply { writeBytes(ByteArray(256 * 1024) { (it * 13).toByte() }) }

    private fun leftovers(): List<File> = dir.walkTopDown().filter { it.name.endsWith(".woka-tmp") }.toList()

    @Test
    fun `a duplicate is the whole file, and no temp is left`() = runTest {
        val file = film()
        FolderActions.duplicateFiles(listOf(file.absolutePath))
        assertContentEquals(file.readBytes(), File(dir, "film copy.mkv").readBytes())
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun `a folder copied elsewhere arrives file by file, each whole`() = runTest {
        val shoot = File(dir, "shoot").apply { mkdirs() }
        val inner = film(File(shoot, "day 1").apply { mkdirs() }, "clip.mkv")
        val elsewhere = File(dir, "elsewhere").apply { mkdirs() }
        FolderActions.copyFiles(listOf(shoot.absolutePath), elsewhere.absolutePath)
        val copied = File(elsewhere, "shoot/day 1/clip.mkv")
        assertTrue(copied.isFile, "the nested file did not arrive")
        assertContentEquals(inner.readBytes(), copied.readBytes())
        assertEquals(emptyList(), leftovers())
    }
}
