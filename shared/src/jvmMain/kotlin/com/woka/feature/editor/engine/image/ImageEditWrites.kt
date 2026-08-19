package com.woka.feature.editor.engine.image

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.save.SourceStamp
import java.io.File

/**
 * The two writes bucket A has — record and revert — as functions of a file.
 *
 * Split out of `ImageEditorEngine` when ME-107 pushed it past the 150-line mandate.
 * They took no engine state beyond the file and the stamp, so this is a move rather
 * than a redesign: the engine still decides *when* to write, re-reads its stamp and
 * evicts the caches, and these decide what the bytes become.
 */

/**
 * Bucket A: the recipe goes into the file and the pixels are not touched.
 *
 * **An emptied document clears rather than reporting nothing to do.** Undoing every
 * operation on a photograph that carries a recipe and pressing `Done` must remove the
 * recipe — it is Revert reached from the other direction (§5.2), and it is the branch
 * that decides whether undo-then-save means anything.
 */
internal fun recordRecipe(
    file: File,
    document: EditDocument,
    stamp: SourceStamp
): EditSaveResult {
    if (!stamp.matches(file)) {
        return EditSaveResult.Failed(
            "${file.name} changed on disk since it was opened. Nothing was written."
        )
    }
    val written =
        if (document.isEmpty) EditRecipe.clear(file) else EditRecipe.write(file, document)
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
