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
 * Duplicating a file into the folder it already lives in (2026-09-03).
 *
 * **This destroyed the photograph.** `copyFiles` resolved the target as
 * `File(destDir, file.name)`, which for the file's own folder *is* the file;
 * `File.copyTo(overwrite = true)` deletes the target before opening the source, so the
 * original was removed and the read then failed with `FileNotFoundException`. Nothing
 * was written and nothing was left — reported from the grid's "Duplicate to…" with the
 * same folder chosen.
 *
 * The assertions are about **the original still being there**, first and separately from
 * anything about the copy. A test that only checked "two files now exist" would pass on
 * an implementation that lost the original and wrote two new ones.
 */
class FolderCopyOntoItselfTest {

    private val dir: File = Files.createTempDirectory("woka-copy-self").toFile()

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    private fun photograph(name: String = "photo.avif"): File =
        File(dir, name).apply { writeBytes(ByteArray(64) { (it * 7).toByte() }) }

    @Test
    fun `the original survives, byte for byte`() = runTest {
        val file = photograph()
        val before = file.readBytes()

        FolderActions.copyFiles(listOf(file.absolutePath), dir.absolutePath)

        assertTrue(file.isFile, "the original was deleted by duplicating it")
        assertContentEquals(before, file.readBytes(), "the original's bytes changed")
    }

    @Test
    fun `and a copy appears beside it under a name of its own`() = runTest {
        val file = photograph()
        FolderActions.copyFiles(listOf(file.absolutePath), dir.absolutePath)

        val copy = File(dir, "photo copy.avif")
        assertTrue(copy.isFile, "no copy was made")
        assertContentEquals(file.readBytes(), copy.readBytes())
    }

    @Test
    fun `duplicating twice numbers the second from one`() = runTest {
        // `copy`, `copy 1`, `copy 2` — the sequence `duplicateFiles` has always given.
        // Preserved deliberately: the guard was added to stop a file being destroyed,
        // not to rename anybody's existing copies.
        val file = photograph()
        FolderActions.copyFiles(listOf(file.absolutePath), dir.absolutePath)
        FolderActions.copyFiles(listOf(file.absolutePath), dir.absolutePath)

        assertTrue(File(dir, "photo copy.avif").isFile)
        assertTrue(File(dir, "photo copy 1.avif").isFile, "the second copy overwrote the first")
        assertEquals(3, dir.listFiles()?.size, "unexpected files in the folder")
    }

    @Test
    fun `a copy into another folder keeps its own name`() = runTest {
        // The guard must not fire where it is not wanted: copying elsewhere is still a
        // copy under the same name, which is what every other caller relies on.
        val file = photograph()
        val elsewhere = File(dir, "sub").apply { mkdirs() }

        FolderActions.copyFiles(listOf(file.absolutePath), elsewhere.absolutePath)

        assertTrue(File(elsewhere, "photo.avif").isFile, "the copy was renamed on the way")
        assertTrue(file.isFile)
    }

    @Test
    fun `moving a file into its own folder leaves it alone`() = runTest {
        // The same trap one function down: `moveFiles` falls back to copy-then-delete,
        // which for a self-move deletes what it just failed to copy.
        val file = photograph()
        val before = file.readBytes()

        FolderActions.moveFiles(listOf(file.absolutePath), dir.absolutePath)

        assertTrue(file.isFile, "moving a file into its own folder deleted it")
        assertContentEquals(before, file.readBytes())
        assertEquals(1, dir.listFiles()?.size, "a self-move left something behind")
    }
}
