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
     * than twenty. A **head** carrier goes through `writeAtomically` — temp beside the
     * target, one atomic move, three retries — because its segment sits in front of the
     * image data and a half-written head is a destroyed photograph.
     *
     * A **tail** carrier does not, since ME-280: its box is the last thing in the file,
     * so it is replaced in place and a failed write cannot reach the picture.
     * `TailRecipeCarrier`'s KDoc holds that argument in full.
     *
     * **Whoever merges is above this.** `recordRecipe` reads, merges and hands the
     * result here; the carrier level replaces, as it always did.
     */
    fun write(file: File, payload: EditPayload): Result<Unit> {
        val carrier = carrierFor(file)
            ?: return failure("${file.extension.uppercase()} files cannot carry an edit.")
        val encoded = runCatching { editorJson.encodeToString(payload) }
            .getOrElse { return Result.failure(it) }

        // **Before the read, which is the whole point** (ME-280): a tail carrier's box is
        // the last thing in the file, so it is replaced with a `setLength` and an append
        // rather than by building a second copy of a 4 GB video.
        if (carrier is TailRecipeCarrier) {
            appendedInPlace(file, carrier, encoded)?.let { return it }
        }
        val bytes = runCatching { file.readBytes() }.getOrElse { return Result.failure(it) }

        val written = carrier.with(carrier.without(bytes) ?: bytes, encoded)
            ?: return failure("This edit is too large for a ${file.extension} to carry.")
        return writeAtomically(file, written)
    }

    /**
     * The in-place write, with this object's two refusals in front of it — they live here
     * because these are the sentences §4.5's banner prints, and `RecipeInPlace` owns bytes
     * rather than words. Null falls through to the byte path.
     */
    private fun appendedInPlace(
        file: File,
        carrier: TailRecipeCarrier,
        encoded: String
    ): Result<Unit>? {
        val head = file.leading(HEAD_PROBE) ?: return null
        if (!carrier.accepts(head)) {
            return failure("${file.extension.uppercase()} files cannot carry an edit.")
        }
        val box = carrier.box(encoded)
            ?: return failure("This edit is too large for a ${file.extension} to carry.")
        return writeInPlace(file, carrier, box)
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
        if (carrier is TailRecipeCarrier) {
            clearInPlace(file, carrier)?.let { return it }
        }
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

/** Enough for a container's magic — `ftyp` sits at offset 4 in every ISOBMFF file. */
private const val HEAD_PROBE = 8

private fun failure(reason: String): Result<Unit> = Result.failure(IllegalArgumentException(reason))
