package com.woka.integration

import com.woka.core.data.THUMBNAIL_BUDGET_BYTES
import com.woka.core.data.WokaStorage
import com.woka.core.data.markUsed
import com.woka.core.data.sweepThumbnails
import com.woka.support.withTempStorageRoot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Big enough that a handful of them crosses the budget the tests set. */
private const val ENTRY_BYTES = 4_096

/**
 * The bound on `.thumbnails`, over a throwaway storage root — **never** the real
 * `Documents/OldValencia/Woka`, which is what `withTempStorageRoot` is for and what
 * a test that deletes files has no business going near.
 *
 * The budget is passed explicitly rather than left at [THUMBNAIL_BUDGET_BYTES]:
 * writing a hundred megabytes to assert a hundred-megabyte cap would make the suite
 * pay for the number instead of the behaviour.
 */
class ThumbnailSweepTest {

    private fun entry(name: String, at: Long): File =
        File(WokaStorage.thumbnailsDir, "$name.webp").apply {
            writeBytes(ByteArray(ENTRY_BYTES))
            setLastModified(at)
        }

    @Test
    fun `a directory already under budget is left alone`() = withTempStorageRoot {
        repeat(4) { entry("keep$it", 1_000_000L + it) }
        val result = sweepThumbnails(budgetBytes = 10L * ENTRY_BYTES)

        assertEquals(0, result.removed)
        assertEquals(4, WokaStorage.thumbnailEntries().size)
        assertEquals(result.bytesBefore, result.bytesAfter)
    }

    @Test
    fun `an over-budget directory is brought back under it`() = withTempStorageRoot {
        repeat(10) { entry("entry$it", 1_000_000L + it * 1_000L) }
        val budget = 4L * ENTRY_BYTES
        val result = sweepThumbnails(budgetBytes = budget)

        assertTrue(result.bytesAfter <= budget, "still ${result.bytesAfter} bytes over $budget")
        assertEquals(10, result.entriesBefore)
        assertEquals(6, result.removed)
        assertEquals(4, WokaStorage.thumbnailEntries().size)
    }

    /** Oldest first, so what survives is what was used most recently. */
    @Test
    fun `the entries that survive are the ones used most recently`() = withTempStorageRoot {
        val old = entry("old", 1_000_000L)
        val middle = entry("middle", 2_000_000L)
        val recent = entry("recent", 3_000_000L)

        sweepThumbnails(budgetBytes = 1L * ENTRY_BYTES)

        assertTrue(recent.isFile, "the most recently used entry was deleted")
        assertTrue(!middle.isFile && !old.isFile, "an older entry survived")
    }

    /**
     * The point of `markUsed`, and the whole reason the ordering means "least recently
     * *used*" rather than "oldest built": the entry written first is the one that
     * survives, because it is the one that was read.
     */
    @Test
    fun `a marked entry outlives a newer one that was never read`() = withTempStorageRoot {
        val built = entry("built-first", 1_000_000L)
        val newer = entry("built-later", 2_000_000L)

        markUsed(built)

        sweepThumbnails(budgetBytes = 1L * ENTRY_BYTES)

        assertTrue(built.isFile, "the entry that was read was evicted anyway")
        assertTrue(!newer.isFile, "the entry nothing ever read survived")
    }

    /**
     * The schema marker lives in the same directory and is not an entry. Deleting it
     * would make the next launch treat the cache as a foreign schema and wipe all of
     * it — a sweep turning into a purge.
     */
    @Test
    fun `the schema marker is never swept`() = withTempStorageRoot {
        val marker = File(WokaStorage.thumbnailsDir, ".keys-v4")
        repeat(6) { entry("entry$it", 1_000_000L + it) }

        sweepThumbnails(budgetBytes = 1L * ENTRY_BYTES)

        assertTrue(marker.isFile, "the sweep deleted the schema marker")
    }
}
