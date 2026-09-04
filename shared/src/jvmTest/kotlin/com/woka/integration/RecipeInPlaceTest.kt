package com.woka.integration

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.IntValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.fixtures.LIVE_PHOTO_STILL
import com.woka.fixtures.SampleMedia
import com.woka.support.withTempStorageRoot
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The in-place write, on a real ISOBMFF container (ME-280).
 *
 * `HeicRecipeTest` proves the *outcome* — a document goes in and comes back, and the
 * pixels do not move — and it goes on doing that unedited. This proves the **way**: the
 * file is truncated and appended rather than rebuilt, which is the difference between a
 * 4 GB video that can carry an edit and one that cannot.
 *
 * Length is the assertion that catches a regression. A byte-path write leaves a correct
 * file of the wrong size — two boxes after the second write — so if `readBytes` finds its
 * way back into this road, the first test below fails.
 */
class RecipeInPlaceTest {

    private fun onCopy(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-in-place").toFile()
        try {
            val file = File(dir, LIVE_PHOTO_STILL)
            file.writeBytes(SampleMedia.file(LIVE_PHOTO_STILL).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Two-digit angles throughout, so every payload here encodes to the **same number of
     * bytes** and a length comparison is about the box count rather than about the JSON.
     */
    private fun document(angle: Int) =
        EditDocument(ops = listOf(ToolOp("transform", ParamSet(mapOf("angle" to IntValue(angle))))))

    /** A `uuid` box that is emphatically not ours — Canon, Adobe and GoPro all write them. */
    private fun foreignBox(): ByteArray {
        val identifier = "Someone-Elses-Box".toByteArray(Charsets.US_ASCII)
        val size = 8 + identifier.size
        return byteArrayOf(0, 0, (size shr 8).toByte(), (size and 0xFF).toByte()) +
            "uuid".toByteArray(Charsets.US_ASCII) + identifier
    }

    @Test
    fun `a second write replaces the box rather than adding one`() = onCopy { file ->
        val original = file.length()

        assertTrue(EditRecipe.write(file, document(90)).isSuccess)
        val afterFirst = file.length()
        assertTrue(afterFirst > original, "the first write appended nothing")

        assertTrue(EditRecipe.write(file, document(45)).isSuccess)

        assertEquals(afterFirst, file.length(), "the old box was not truncated away")
        assertEquals(document(45).ops, assertNotNull(EditRecipe.read(file)).ops)
    }

    @Test
    fun `clearing returns the file to the byte it started at`() = onCopy { file ->
        val original = file.readBytes()

        assertTrue(EditRecipe.write(file, document(90)).isSuccess)
        assertTrue(EditRecipe.clear(file).isSuccess)

        assertContentEquals(original, file.readBytes())
    }

    /**
     * `EditRecipe.clear`'s own rule: an mtime is a thumbnail cache key and the
     * stale-source signal, so a file with nothing to remove is not opened at all.
     */
    @Test
    fun `clearing a file that carries nothing does not touch it`() = onCopy { file ->
        file.setLastModified(1_600_000_000_000L)
        val was = file.lastModified()

        assertTrue(EditRecipe.clear(file).isSuccess)

        assertEquals(was, file.lastModified())
    }

    /**
     * The one shape that must fall back to the byte path: truncating to our box's start
     * would delete whatever another program appended after it, which is a larger harm
     * than the whole-file copy this ticket removes.
     */
    @Test
    fun `foreign bytes after our box survive a rewrite`() = onCopy { file ->
        assertTrue(EditRecipe.write(file, document(90)).isSuccess)
        val foreign = foreignBox()
        RandomAccessFile(file, "rw").use { handle ->
            handle.seek(file.length())
            handle.write(foreign)
        }

        assertTrue(EditRecipe.write(file, document(45)).isSuccess)

        assertTrue(file.readBytes().containsBytes(foreign), "somebody else's box was truncated away")
        assertEquals(document(45).ops, assertNotNull(EditRecipe.read(file)).ops)
    }
}

private fun ByteArray.containsBytes(what: ByteArray): Boolean =
    (0..size - what.size).any { at -> what.indices.all { this[at + it] == what[it] } }
