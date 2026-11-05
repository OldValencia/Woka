package com.woka.feature.editor.engine.video

import com.woka.core.ui.image.AppliedVideoEdits
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.engine.image.recordRecipe
import com.woka.feature.editor.engine.image.revertRecipe
import com.woka.feature.editor.save.SourceStamp
import com.woka.feature.editor.save.evictCachesFor
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.WHOLE_SOURCE_ID
import java.io.File

/**
 * Every write the video engine has — the save, the revert, and the stamp both of them
 * carry (ME-288).
 *
 * `ImageEditWrites`' cut, made for the same reason and at the same place: `save` and
 * `revert` pushed `VideoEditorEngine` past the size mandate, and neither of them touches a
 * player, a recipe or a frame. The engine still decides *when* to write; this decides what
 * happens when it does.
 *
 * **A class rather than functions**, unlike its image sibling, because [stamp] is state:
 * it has to be re-read after a successful write and there is nowhere else for it to live
 * that is not the engine.
 */
internal class VideoEditorWrites(
    private val file: File,
    /** Passed in, never re-derived: the factory has already asked. */
    private val capability: EditCapability
) {

    /**
     * Re-read after every successful write, for `ImageEditorEngine`'s stated reason: a
     * save lands on the file the editor is still holding open, and without this the second
     * `Done` of a sitting reports the user's own first one as somebody else's change.
     */
    private var stamp = SourceStamp.of(file)

    /**
     * **The image engine's write, not a second one.** `recordRecipe` reads the payload,
     * merges and replaces; what this adds is the stamp and the eviction, exactly as
     * `ImageEditorEngine.save` does. Two merge implementations would disagree, and the one
     * that lost would lose somebody's edit.
     *
     * The `else` is `RenderOnly` (ME-289): a container with nowhere to keep the edit. The
     * bar offers `Render video…` in `Done`'s place, so only the quit dialog's `Save` lands
     * here — answered with a sentence rather than an `error()`, because a save that throws
     * is the one failure §4.5's banner cannot describe.
     */
    fun save(layers: EditLayers): EditSaveResult = afterWriting(
        when (capability) {
            EditCapability.Recorded -> recordRecipe(file, layers.onlyLiveClips(storedVideoPayload(file).frames.keys), stamp)
            else -> EditSaveResult.Failed(
                "${file.extension.uppercase()} videos cannot keep an edit yet."
            )
        }
    )

    /**
     * Back to as shot — the box removed and nothing else, since the media data was never
     * rewritten in the first place. Under This Clip, only that clip's look goes and the
     * cut and the shared layer stay (ME-215): `revertRecipe`'s frame road, keyed on the clip.
     */
    fun revert(scope: EditScope = EditScope.AllFrames, key: Int = 0): EditSaveResult =
        afterWriting(revertRecipe(file, scope, key))

    /** The two things that follow any successful write, shared by both of them. */
    private fun afterWriting(result: EditSaveResult): EditSaveResult {
        if (result is EditSaveResult.Saved) {
            stamp = SourceStamp.of(file)
            evictCachesFor(file)
            // The viewer underneath, its slider and the grid re-read the film (ME-284).
            AppliedVideoEdits.changed()
        }
        return result
    }
}

/**
 * [EditLayers] keeping only the looks of clips the cut still holds (ME-215): a deleted
 * clip's layer — this sitting's or one the file already [stored] — is marked touched and
 * left out, so the file loses its key rather than carrying a look nothing draws.
 */
private fun EditLayers.onlyLiveClips(stored: Set<Int>): EditLayers {
    val keys = (TrimTool.clipsIn(base) ?: listOf(TrimClip(WHOLE_SOURCE_ID, 0L, 0L))).map { it.key }.toSet()
    // A cut reset or joined back to the whole film is no edit, and is not written as one —
    // or the file stays marked edited with nothing to show for it (reported 2026-11-04).
    val cut = base.copy(ops = base.ops.filterNot { it.toolId == TrimTool.manifest.id && TrimTool.clipsIn(EditDocument(listOf(it))) == null })
    return copy(base = cut, frames = frames.filterKeys { it in keys }, touched = touched + frames.keys + stored - keys)
}
