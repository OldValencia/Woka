package com.woka.core.data

import java.io.File

/**
 * How much disk `.thumbnails` may hold. A hundred megabytes is the number the
 * directory had reached, by hand, when it was reported (2026-08-30) — a bound is
 * only meaningful against the amount that provoked it.
 */
const val THUMBNAIL_BUDGET_BYTES: Long = 100L * 1024 * 1024

/** What one sweep did, for the caller to log and for a test to assert on. */
data class ThumbnailSweepResult(
    val entriesBefore: Int,
    val bytesBefore: Long,
    val removed: Int,
    val bytesAfter: Long
)

/**
 * Brings `.thumbnails` back under [budgetBytes], least recently used first.
 *
 * **Why a bound and not a repair.** An entry is named by a hash of its source's path,
 * length and mtime, and the path cannot be recovered from it — so given an orphan
 * there is no way to ask whether its source still exists. Every leak the directory
 * has (an edit changes length and mtime; a rename or a delete strands the path; a
 * frame set multiplies both) produces an entry nothing can identify afterwards. A
 * budget is the one policy that does not need to.
 *
 * **Least recently used, not oldest built.** Ordering by `lastModified` alone would be
 * FIFO: an entry is written once and never touched again, so a poster opened daily and
 * one never opened again are indistinguishable. `markUsed` is what makes the ordering
 * mean what this ticket asked for — "delete what has not been looked at in a long
 * time" — without the index the liveness policy would have needed.
 *
 * **By bytes, not by count**, for the same reason `ImageBitmapCache` is: the entries
 * are not one size, and a cap on how many there are bounds nothing.
 *
 * Deletion failures are counted as not deleted rather than thrown: a poster held open
 * by something else is a reason to leave it and sweep again next launch, not a reason
 * to take the launch down.
 */
fun sweepThumbnails(budgetBytes: Long = THUMBNAIL_BUDGET_BYTES): ThumbnailSweepResult {
    val entries = WokaStorage.thumbnailEntries()
    val bytesBefore = entries.sumOf { it.length() }
    if (bytesBefore <= budgetBytes) {
        return ThumbnailSweepResult(entries.size, bytesBefore, removed = 0, bytesAfter = bytesBefore)
    }

    var held = bytesBefore
    var removed = 0
    for (entry in entries.sortedBy { it.lastModified() }) {
        if (held <= budgetBytes) break
        val size = entry.length()
        if (entry.delete()) {
            held -= size
            removed++
        }
    }
    return ThumbnailSweepResult(entries.size, bytesBefore, removed, held)
}

/**
 * Records that an entry was just served, so the sweep above can tell a poster in daily
 * use from one built months ago and never looked at again.
 *
 * A metadata write on every cache hit, which is what makes it cheap enough to do
 * unconditionally: the alternative was the file system's own access time, and on
 * Windows that is routinely disabled and cannot be relied on.
 *
 * Silent on failure. Losing the mark costs an entry its place in the ordering, which
 * is a worse eviction decision and never a wrong picture.
 */
fun markUsed(entry: File) {
    runCatching { entry.setLastModified(System.currentTimeMillis()) }
}
