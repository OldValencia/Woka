package com.woka.feature.editor.engine.video

import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.engine.image.recordRecipe
import com.woka.feature.editor.engine.image.revertRecipe
import com.woka.feature.editor.save.SourceStamp
import com.woka.feature.editor.save.evictCachesFor
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
     * The `else` is a container ME-282 or ME-283 has not reached yet. It is unreachable
     * through the UI — the factory builds no engine for one — and answered with a sentence
     * rather than an `error()`, because a save that throws is the one failure §4.5's banner
     * cannot describe.
     */
    fun save(layers: EditLayers): EditSaveResult = afterWriting(
        when (capability) {
            EditCapability.Recorded -> recordRecipe(file, layers, stamp)
            else -> EditSaveResult.Failed(
                "${file.extension.uppercase()} videos cannot keep an edit yet."
            )
        }
    )

    /**
     * Back to as shot — the box removed and nothing else, since the media data was never
     * rewritten in the first place.
     *
     * It takes no scope and no frame, and the interface licenses that: a video timeline has
     * no frame to key on, so `EditorFrames()` stays at one picture, the scope switch never
     * appears, and `revertRecipe`'s own defaults are the only meaning available.
     */
    fun revert(): EditSaveResult = afterWriting(revertRecipe(file))

    /** The two things that follow any successful write, shared by both of them. */
    private fun afterWriting(result: EditSaveResult): EditSaveResult {
        if (result is EditSaveResult.Saved) {
            stamp = SourceStamp.of(file)
            evictCachesFor(file)
        }
        return result
    }
}
