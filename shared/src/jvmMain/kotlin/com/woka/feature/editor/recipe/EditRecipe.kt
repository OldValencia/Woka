package com.woka.feature.editor.recipe

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditPayload
import com.woka.feature.editor.core.editorJson
import com.woka.feature.editor.save.writeAtomically
import kotlinx.serialization.encodeToString
import java.io.File

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

    /** Cheap enough to ask while drawing a grid — see [slice]. */
    fun carries(file: File): Boolean = payloadOf(file) != null

    fun read(file: File): EditPayload? {
        val payload = payloadOf(file) ?: return null
        return runCatching { editorJson.decodeFromString<EditPayload>(payload) }.getOrNull()
    }

    /**
     * What one frame of [file] draws — the reader's question, and nothing else
     * (ME-126).
     *
     * `RecipeStillEdits` and `ImageEditorEngine` both want a document for a frame
     * and neither has any use for the payload around it. Null for a file carrying
     * nothing, so a caller can still tell "no recipe" from "a recipe drawing
     * nothing".
     */
    fun documentIn(file: File, frameIndex: Int): EditDocument? =
        read(file)?.documentFor(frameIndex)

    /**
     * Replaces whatever [file] carried with [payload].
     *
     * Clears first, so twenty edits of the same photograph leave one segment rather
     * than twenty. Written through `writeAtomically` — temp beside the target, one
     * atomic move, three retries — because a half-written recipe is a destroyed
     * photograph, and a small write earns that no less than a re-encode does.
     *
     * **Whoever merges is above this.** `recordRecipe` reads, merges and hands the
     * result here; the carrier level replaces, as it always did.
     */
    fun write(file: File, payload: EditPayload): Result<Unit> {
        val carrier = carrierFor(file)
            ?: return failure("${file.extension.uppercase()} files cannot carry an edit.")
        val bytes = runCatching { file.readBytes() }.getOrElse { return Result.failure(it) }
        val encoded = runCatching { editorJson.encodeToString(payload) }
            .getOrElse { return Result.failure(it) }

        val written = carrier.with(carrier.without(bytes) ?: bytes, encoded)
            ?: return failure("This edit is too large for a ${file.extension} to carry.")
        return writeAtomically(file, written)
    }

    /**
     * The same write for a caller that has no frame in mind: one document, every
     * frame, no keys — which is what a recipe meant before ME-126 and what it still
     * means for the overwhelmingly common file holding one picture.
     */
    fun write(file: File, document: EditDocument): Result<Unit> =
        write(file, EditPayload(ops = document.ops))

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

private fun failure(reason: String): Result<Unit> = Result.failure(IllegalArgumentException(reason))
