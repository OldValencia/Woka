package com.woka.feature.editor.recipe

import java.io.File
import java.io.RandomAccessFile

/**
 * Writing and removing a [TailRecipeCarrier]'s box without copying the file (ME-280).
 *
 * The other half of `RecipeSlices`: that one reads a tail cheaply, this one writes one
 * cheaply, and neither knows what a document is. `EditRecipe` still owns every message a
 * user reads and is still the only thing that decides *what* goes in.
 *
 * **Both functions answer `null` for "this file cannot be written in place", not for
 * "it failed"** — a failure is a `Result.failure` like anywhere else. Null means the
 * caller must fall back to the byte path, and there is exactly one shape that produces
 * it: our box with somebody else's bytes after it (see [tailWrite]). A nullable `Result`
 * is otherwise a puzzle, which is why it is spelled out here.
 *
 * Why abandoning `writeAtomically` is safe for these carriers and for no others is
 * [TailRecipeCarrier]'s own KDoc, and it is the whole argument for this file existing.
 */

/**
 * Truncates back over the box already there and appends [box].
 *
 * One handle, opened `"rw"`, and the file is never read: `setLength` down to the offset,
 * seek, write. On a file carrying nothing the offset *is* the length, so the `setLength`
 * is a no-op and this is a plain append.
 */
internal fun writeInPlace(file: File, carrier: TailRecipeCarrier, box: ByteArray): Result<Unit>? {
    val write = tailWrite(file, carrier) ?: return null
    return runCatching {
        RandomAccessFile(file, "rw").use { handle ->
            handle.setLength(write.at)
            handle.seek(write.at)
            handle.write(box)
        }
    }
}

/**
 * Removes the box, returning the file to as-shot.
 *
 * **A file carrying nothing is not opened at all**, which is `EditRecipe.clear`'s own
 * rule: rewriting it would move its mtime — a thumbnail cache key and the stale-source
 * signal — for no change to its contents.
 */
internal fun clearInPlace(file: File, carrier: TailRecipeCarrier): Result<Unit>? {
    val write = tailWrite(file, carrier) ?: return null
    if (!write.carries) return Result.success(Unit)
    return runCatching {
        RandomAccessFile(file, "rw").use { handle -> handle.setLength(write.at) }
    }
}

/** Where the next write starts, and whether it is replacing a box or appending one. */
private class TailWrite(val at: Long, val carries: Boolean)

/**
 * The three-way answer both functions above rest on.
 *
 * - **No box of ours** → the end of the file, so a write is an append and a clear is
 *   nothing at all.
 * - **Ours, ending where the file does** → its start, so a write replaces it.
 * - **Ours, with bytes after it** → **null**. Another program appended something of its
 *   own, and truncating to our box's start would delete *their* data — a larger harm than
 *   the one this file removes. The byte path splices correctly, so the caller uses it.
 *
 * The offset is `length - tail.size + start` because [carrier] is handed a slice, not a
 * file. That arithmetic is only sound because `TailRecipeCarrier.box` refuses a payload
 * larger than `TAIL_WINDOW`, so our box is always wholly inside the slice a reader gets.
 */
private fun tailWrite(file: File, carrier: TailRecipeCarrier): TailWrite? {
    val length = file.length()
    val tail = file.slice(carrier.window) ?: return null
    val box = carrier.boxIn(tail) ?: return TailWrite(length, carries = false)
    if (box.last + 1 != tail.size) return null
    return TailWrite(length - tail.size + box.first, carries = true)
}
