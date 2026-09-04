package com.woka.feature.editor.engine.image

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.tools.hdr.bakesToSdr
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditPayload
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.save.ImageSaveJob
import com.woka.feature.editor.save.SourceStamp
import java.io.File

/**
 * Every write the image engine has — the branch, the record and the revert — as
 * functions of a file.
 *
 * Split out of `ImageEditorEngine` when ME-107 pushed it past the 150-line mandate,
 * and grown by ME-126 with the capability branch for the same reason. They take no
 * engine state beyond the file and the stamp, so this is a move rather than a
 * redesign: the engine still decides *when* to write, re-reads its stamp and evicts
 * the caches, and these decide what the bytes become.
 */

/**
 * §5.1's three buckets, and nothing else decides which write a file gets.
 *
 * Lifted out of `ImageEditorEngine.save` by ME-126, which needed room in that file
 * and had no reason to leave a `when` over a value the engine only carries. The
 * engine still owns the stamp and the cache eviction; this owns the branch.
 *
 * [EditCapability.Unavailable] is unreachable through the UI — the factory builds no
 * engine for it — and is answered honestly rather than by an `error()`, because a save
 * that throws is the one failure mode §4.5's banner cannot describe.
 *
 * [reEncode] is passed rather than called: bucket B renders the pixels through the
 * engine's own recipe, which is `internal` to it by design (§3.3).
 */
internal fun writeFor(
    capability: EditCapability,
    file: File,
    layers: EditLayers,
    /** The frame on screen, which is the one bucket B re-encodes. */
    frameIndex: Int,
    stamp: SourceStamp,
    reEncode: (EditDocument) -> EditSaveResult
): EditSaveResult {
    val open = EditDocument(layers.base.ops + (layers.frames[frameIndex]?.ops ?: emptyList()))
    // **Ahead of the capability switch, deliberately** (ME-118). `To SDR` rewrites the
    // photograph, so it leaves bucket A whatever the format's carrier can do — a recipe
    // spliced beside baked pixels would replay the edit on top of itself.
    if (capability != EditCapability.Unavailable && bakesToSdr(open)) return reEncode(open)
    return writeByCapability(capability, file, layers, open, stamp, reEncode)
}

private fun writeByCapability(
    capability: EditCapability,
    file: File,
    layers: EditLayers,
    open: EditDocument,
    stamp: SourceStamp,
    reEncode: (EditDocument) -> EditSaveResult
): EditSaveResult = when (capability) {
    EditCapability.Recorded -> recordRecipe(file, layers, stamp)
    // **Unreachable for an image**, and here so the `when` stays total: the factory
    // answers `Preview` for a video alone, and a video is not written by this function
    // at all. `VideoEditorEngine.save` returns the same sentence, and ME-280 deletes
    // both the case and this branch.
    EditCapability.Preview -> EditSaveResult.Failed("Video edits cannot be saved yet.")
    // **Bucket B takes the open frame alone** and could not take more: it rewrites the
    // picture rather than recording a recipe (ME-126). It is empty today.
    EditCapability.Destructive -> reEncode(open)
    EditCapability.Unavailable -> EditSaveResult.Failed(
        "${file.extension.uppercase()} files cannot be edited."
    )
}

/**
 * Bucket A: the recipe goes into the file and the pixels are not touched.
 *
 * **It merges rather than replaces** (ME-126). The payload a file carries can hold an
 * edit for a frame this sitting never opened, so `Done` reads what is there, folds
 * [document] into the slot [scope] names and writes the result — the same loss ME-134
 * fixed one level up, now at frame granularity.
 *
 * **An emptied payload clears rather than reporting nothing to do.** Undoing every
 * operation on a photograph that carries a recipe and pressing `Done` must remove the
 * recipe — it is Revert reached from the other direction (§5.2), and it is the branch
 * that decides whether undo-then-save means anything. Asked of the *merged* payload,
 * so a frame emptied while another still carries work leaves the recipe standing.
 */
internal fun recordRecipe(
    file: File,
    layers: EditLayers,
    stamp: SourceStamp
): EditSaveResult {
    if (!stamp.matches(file)) {
        return EditSaveResult.Failed(
            "${file.name} changed on disk since it was opened. Nothing was written."
        )
    }
    val existing = EditRecipe.read(file) ?: EditPayload()

    // **The base first, then each frame's own on top.** `with` stores a frame's document
    // minus the payload's base, so handing it base-plus-delta *after* the base has landed
    // stores exactly the delta — the order is not free.
    val based = existing.with(layers.base, EditScope.AllFrames, 0)
    val next = layers.frames.entries.fold(based) { payload, (frame, own) ->
        payload.with(EditDocument(payload.ops + own.ops), EditScope.SingleFrame, frame)
    }
    // Never opened keeps what the file said; opened and emptied loses its key.
    val kept = next.frames.filterKeys { it in layers.frames || it !in layers.touched }
    val pruned = next.copy(frames = kept)

    val written = if (pruned.isEmpty) EditRecipe.clear(file) else EditRecipe.write(file, pruned)
    return written.fold(
        onSuccess = { EditSaveResult.Saved(file.absolutePath) },
        onFailure = { writeFailed(file) }
    )
}

/**
 * Bucket B: no slot to record in, so the picture itself is re-encoded in place.
 *
 * **It takes no [EditScope] and could not use one** (ME-126): there is no payload
 * to key, the pixels themselves are rewritten, and what a frame set would mean
 * under a re-encode is a question nothing has asked — bucket B is empty today
 * (§5.1).
 *
 * Moved off `ImageEditorEngine` by ME-302, which needed room in that file for one
 * property. The engine still decides *when* to re-encode and still owns [recipeOf],
 * which is `internal` to it by design (§3.3); this owns nothing but the call.
 */
internal fun reEncodeStill(
    file: File,
    frameIndex: Int,
    sourceSize: IntSize,
    stamp: SourceStamp,
    recipeOf: (EditDocument) -> RasterRecipe,
    document: EditDocument
): EditSaveResult =
    ImageSaveJob(file, frameIndex, sourceSize, stamp, recipeOf).save(document)

/** Shared with `ImageRevertWrites`: one message for every refused write. */
internal fun writeFailed(file: File) =
    EditSaveResult.Failed("${file.name} could not be written. The picture is untouched.")
