package com.woka.feature.editor.recipe

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.editorJson
import com.woka.feature.editor.save.writeAtomically
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.RandomAccessFile

/**
 * How large a slice of a file is enough to answer "does this carry a recipe".
 *
 * The head carriers stop walking where the picture data begins, so for a file either
 * of them wrote the answer is in the first few kilobytes. The window exists for the
 * pathological case — a JPEG whose leading `APPn` run holds a large ICC profile —
 * and [read] falls back to the whole file only when a walk actually ran out.
 *
 * This matters because the grid asks the question per visible tile (ME-133).
 * Reading a 5 MB photograph to find out it has never been edited is the difference
 * between a scroll and a stutter.
 */
private const val HEAD_BYTES = 512 * 1024

/**
 * The same question from the other end (ME-108), and far smaller: a tail carrier's box
 * is the **last** thing in the file, so a slice this size holds it and its header with
 * room to spare. Only a file rewritten by another program — appending something of its
 * own after ours — needs more, and that falls back to the whole file like any other
 * walk that ran out.
 */
internal const val TAIL_WINDOW = 64 * 1024

/**
 * The edit a file carries, as far as the rest of the app is concerned (§5).
 *
 * The pixels are never read, decoded or written here. A recipe is a few hundred
 * bytes of JSON living beside the picture in the container's own private slot, so
 * writing one leaves the photograph byte-identical and removing one returns the
 * file to exactly what came off the camera.
 *
 * Round-trips through `editorJson`, which ME-003 kept separate from the settings
 * instance and made `ignoreUnknownKeys` for precisely this: a recipe written by a
 * later build must degrade in an earlier one rather than make it refuse the file.
 */
internal object EditRecipe {

    /** Cheap enough to ask while drawing a grid — see [HEAD_BYTES]. */
    fun carries(file: File): Boolean = payloadOf(file) != null

    fun read(file: File): EditDocument? {
        val payload = payloadOf(file) ?: return null
        return runCatching { editorJson.decodeFromString<EditDocument>(payload) }.getOrNull()
    }

    /**
     * Replaces whatever [file] carried with [document].
     *
     * Clears first, so twenty edits of the same photograph leave one segment rather
     * than twenty. Written through `writeAtomically` — temp beside the target, one
     * atomic move, three retries — because a half-written recipe is a destroyed
     * photograph, and a small write earns that no less than a re-encode does.
     */
    fun write(file: File, document: EditDocument): Result<Unit> {
        val carrier = carrierFor(file)
            ?: return failure("${file.extension.uppercase()} files cannot carry an edit.")
        val bytes = runCatching { file.readBytes() }.getOrElse { return Result.failure(it) }
        val payload = runCatching { editorJson.encodeToString(document) }
            .getOrElse { return Result.failure(it) }

        val written = carrier.with(carrier.without(bytes) ?: bytes, payload)
            ?: return failure("This edit is too large for a ${file.extension} to carry.")
        return writeAtomically(file, written)
    }

    /**
     * Removes the recipe, returning the picture to as-shot.
     *
     * A file carrying nothing is **not written to at all**: rewriting it would
     * change its mtime, which is both a thumbnail cache key and the stale-source
     * signal, for no change at all to its contents.
     */
    fun clear(file: File): Result<Unit> {
        val carrier = carrierFor(file) ?: return Result.success(Unit)
        val bytes = runCatching { file.readBytes() }.getOrElse { return Result.failure(it) }
        val cleared = carrier.without(bytes) ?: return Result.success(Unit)
        return writeAtomically(file, cleared)
    }

    private fun payloadOf(file: File): String? {
        val carrier = carrierFor(file) ?: return null
        val slice = file.slice(carrier.window) ?: return null
        val scan = carrier.scan(slice)
        if (scan.payload != null || !scan.truncated || slice.size.toLong() >= file.length()) {
            return scan.payload
        }
        // The walk ran out of window rather than out of file. Rare, and the only
        // case that costs a second read.
        val whole = runCatching { file.readBytes() }.getOrNull() ?: return null
        return carrier.scan(whole).payload
    }
}

private fun File.slice(window: RecipeWindow): ByteArray? = when (window) {
    RecipeWindow.Head -> head()
    RecipeWindow.Tail -> tail()
}

private fun File.head(): ByteArray? = runCatching {
    inputStream().use { stream -> stream.readNBytes(minOf(length(), HEAD_BYTES.toLong()).toInt()) }
}.getOrNull()

/**
 * The last [TAIL_BYTES] of the file, or all of it when it is smaller.
 *
 * A `RandomAccessFile` rather than a skip-and-read, because skipping is not a seek on
 * every stream and this runs per visible grid tile.
 */
private fun File.tail(): ByteArray? = runCatching {
    val size = length()
    val from = maxOf(0L, size - TAIL_WINDOW)
    RandomAccessFile(this, "r").use { handle ->
        handle.seek(from)
        ByteArray((size - from).toInt()).also { handle.readFully(it) }
    }
}.getOrNull()

private fun failure(reason: String): Result<Unit> = Result.failure(IllegalArgumentException(reason))
