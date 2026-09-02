package com.woka.feature.editor.engine.image

import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.recipe.EditRecipe
import java.io.File

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
internal fun revertRecipe(
    file: File,
    scope: EditScope = EditScope.AllFrames,
    frameIndex: Int = 0
): EditSaveResult {
    if (!EditRecipe.carries(file)) return EditSaveResult.NothingToDo
    if (scope == EditScope.SingleFrame) return revertFrame(file, frameIndex)
    return EditRecipe.clear(file).fold(
        onSuccess = { EditSaveResult.Saved(file.absolutePath) },
        onFailure = { writeFailed(file) }
    )
}

/**
 * One frame's own edit removed, and the file's left drawing (ME-176).
 *
 * **The key goes, rather than being emptied to nothing.** `frames[3] = EditDocument()`
 * is a real value under ME-175 — it means "frame 3 adds nothing on top" — and that is
 * indistinguishable on screen from having no key at all, so keeping one would leave the
 * scope switch reading `Single Frame` for a frame with nothing of its own.
 *
 * What is left may be nothing at all, and then the recipe goes with it: §5.2's rule is
 * that a payload drawing nothing is not carried, and `isEmpty` is where it is stated.
 */
private fun revertFrame(file: File, frameIndex: Int): EditSaveResult {
    val payload = EditRecipe.read(file) ?: return EditSaveResult.NothingToDo
    if (!payload.frames.containsKey(frameIndex)) return EditSaveResult.NothingToDo

    val left = payload.copy(frames = payload.frames - frameIndex)
    val write = if (left.isEmpty) EditRecipe.clear(file) else EditRecipe.write(file, left)
    return write.fold(
        onSuccess = { EditSaveResult.Saved(file.absolutePath) },
        onFailure = { writeFailed(file) }
    )
}
