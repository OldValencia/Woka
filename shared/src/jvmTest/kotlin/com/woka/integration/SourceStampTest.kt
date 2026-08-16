package com.woka.integration

import com.woka.feature.editor.save.SourceStamp
import com.woka.support.withTempStorageRoot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The stale-source guard: a file that moved under the editor must not be written. */
class SourceStampTest {

    @Test
    fun `an untouched file still matches`() = withTempStorageRoot { root ->
        val file = File(root, "photo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(SourceStamp.of(file).matches(file))
    }

    @Test
    fun `a file rewritten after the stamp does not match`() = withTempStorageRoot { root ->
        val file = File(root, "photo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val stamp = SourceStamp.of(file)

        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        assertFalse(stamp.matches(file))
    }

    @Test
    fun `a same-length rewrite is caught by the timestamp`() = withTempStorageRoot { root ->
        val file = File(root, "photo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val stamp = SourceStamp.of(file)

        // Byte count preserved — common in formats with fixed-size headers, and the
        // exact case length alone would miss.
        file.writeBytes(byteArrayOf(4, 5, 6))
        file.setLastModified(stamp.lastModifiedMs + 2_000)

        assertFalse(stamp.matches(file))
    }

    @Test
    fun `a deleted source does not match`() = withTempStorageRoot { root ->
        val file = File(root, "photo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val stamp = SourceStamp.of(file)

        file.delete()

        assertFalse(stamp.matches(file), "writing to a source that is gone is not a save")
    }
}
