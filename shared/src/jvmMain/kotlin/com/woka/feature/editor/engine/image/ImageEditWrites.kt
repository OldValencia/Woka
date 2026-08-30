package com.woka.feature.editor.engine.image

import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditPayload
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.recipe.EditRecipe
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
    document: EditDocument,
    scope: EditScope,
    frameIndex: Int,
    stamp: SourceStamp,
    reEncode: (EditDocument) -> EditSaveResult
): EditSaveResult = when (capability) {
    EditCapability.Recorded -> recordRecipe(file, document, scope, frameIndex, stamp)
    EditCapability.Destructive -> reEncode(document)
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
    document: EditDocument,
    scope: EditScope,
    frameIndex: Int,
    stamp: SourceStamp
): EditSaveResult {
    if (!stamp.matches(file)) {
        return EditSaveResult.Failed(
            "${file.name} changed on disk since it was opened. Nothing was written."
        )
    }
    val existing = EditRecipe.read(file) ?: EditPayload()
    val next = existing.with(document, scope, frameIndex)
    val written = if (next.isEmpty) EditRecipe.clear(file) else EditRecipe.write(file, next)
    return written.fold(
        onSuccess = { EditSaveResult.Saved(file.absolutePath) },
        onFailure = { writeFailed(file) }
    )
}

/**
 * Revert: the file goes back to exactly what came off the camera (ME-107).
 *
 * There is no backup store and there does not need to be one — the original pixels
 * never left the file, so this deletes a few hundred bytes of metadata. It is also
 * the whole of "remove Woka's data from this photograph": the segment holds the
 * serialised document and nothing else (§5.2).
 *
 * **No stale-source guard.** A save writes something of the user's over a file that
 * may have moved on; this only removes what Woka itself put there, and refusing that
 * because the picture was touched elsewhere would leave a segment nobody can delete.
 */
internal fun revertRecipe(file: File): EditSaveResult {
    if (!EditRecipe.carries(file)) return EditSaveResult.NothingToDo
    return EditRecipe.clear(file).fold(
        onSuccess = { EditSaveResult.Saved(file.absolutePath) },
        onFailure = { writeFailed(file) }
    )
}

private fun writeFailed(file: File) =
    EditSaveResult.Failed("${file.name} could not be written. The picture is untouched.")
