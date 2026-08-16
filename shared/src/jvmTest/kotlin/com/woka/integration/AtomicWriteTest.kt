package com.woka.integration

import com.woka.feature.editor.save.writeAtomically
import com.woka.support.withTempStorageRoot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun tempOf(target: File) = File(target.parentFile, "${target.name}.woka-tmp")

/**
 * The one part of the editor that can destroy a file. Every case here is about
 * what is left on disk **after** something goes wrong (§5).
 */
class AtomicWriteTest {

    @Test
    fun `replacing an existing file lands the new bytes and leaves no temp`() =
        withTempStorageRoot { root ->
            val target = File(root, "photo.png")
            target.writeBytes(byteArrayOf(1, 2, 3))

            val result = writeAtomically(target, byteArrayOf(9, 9, 9, 9))

            assertTrue(result.isSuccess)
            assertContentEquals(byteArrayOf(9, 9, 9, 9), target.readBytes())
            assertFalse(tempOf(target).exists(), "the temp file outlived the write")
        }

    @Test
    fun `writing where there was nothing still works`() = withTempStorageRoot { root ->
        val target = File(root, "new.png")

        assertTrue(writeAtomically(target, byteArrayOf(7)).isSuccess)

        assertContentEquals(byteArrayOf(7), target.readBytes())
        assertFalse(tempOf(target).exists())
    }

    @Test
    fun `a failed write reports failure and cleans up after itself`() =
        withTempStorageRoot { root ->
            // A non-empty directory cannot be replaced by a move on any platform,
            // which makes this a deterministic failure rather than a timing trick.
            val target = File(root, "occupied").apply { mkdirs() }
            File(target, "inside.txt").writeText("keep me")

            val result = writeAtomically(target, byteArrayOf(1, 2, 3))

            assertTrue(result.isFailure)
            assertFalse(tempOf(target).exists(), "a failure left its temp file behind")
            // The thing that was there is untouched.
            assertTrue(target.isDirectory)
            assertEquals("keep me", File(target, "inside.txt").readText())
        }

    @Test
    fun `a failing write actually retries rather than giving up at once`() =
        withTempStorageRoot { root ->
            val target = File(root, "occupied").apply { mkdirs() }
            File(target, "inside.txt").writeText("keep me")

            val startMs = System.nanoTime() / 1_000_000
            writeAtomically(target, byteArrayOf(1))
            val elapsedMs = System.nanoTime() / 1_000_000 - startMs

            // Three attempts, two 120 ms gaps between them. Without the retries a
            // file held open for a frame by the viewer would fail the save outright.
            assertTrue(elapsedMs >= 240, "gave up after ${elapsedMs}ms — retries are gone")
        }
}
