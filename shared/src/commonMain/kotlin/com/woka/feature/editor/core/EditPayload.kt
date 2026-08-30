package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * What a file's recipe slot actually holds (ME-126): one edit for the whole file,
 * plus an optional override per frame.
 *
 * **Compatibility is free, and that is why it is a sibling field rather than a
 * wrapper.** A recipe written before this type is `{"ops":[…]}` — `editorJson`
 * leaves defaults out — which decodes here with [frames] empty, and empty is
 * exactly "every frame". Not a migration, not a version marker, not a fallback
 * parse: the old form *is* a valid new one. From the other side, an older build
 * decoding `{"frames":{"3":{…}}}` as an `EditDocument` drops the unknown key
 * (`ignoreUnknownKeys`, ME-003) and draws the picture unedited rather than refusing
 * the file — the degradation `code-style/model.md` asks for.
 *
 * **`EditDocument.pending` is deliberately absent.** An operation in flight is a
 * drag, not a record; it only ever reached a file when `Done` landed mid-gesture.
 * [with] takes the committed [EditDocument.ops] and nothing else.
 *
 * No carrier knows about any of this. Every `RecipeCarrier` — JPEG's `APPn`, PNG's
 * chunk, the ISOBMFF `uuid` box, the RIFF chunk, GIF's Application Extension, the
 * TIFF IFD tag — carries an opaque payload string, and six formats are untouched.
 */
@Immutable
@Serializable
data class EditPayload(
    /** What every frame draws unless it carries its own. */
    val ops: List<ToolOp> = emptyList(),
    /**
     * Frames that carry their own edit, by the decoder's own frame index.
     *
     * **An empty document is a real value.** `frames[3] = EditDocument()` means
     * "frame 3: nothing", which is the only way to say *this frame alone is
     * unedited while the others are not* — so an emptied override keeps its key
     * instead of being pruned.
     */
    val frames: Map<Int, EditDocument> = emptyMap()
) {

    /**
     * Whether this payload folds to nothing at all, keys included.
     *
     * §5.2's rule reaches frame granularity through this: a payload that draws
     * nothing removes the recipe, exactly as Revert does from the other direction.
     */
    val isEmpty: Boolean get() = ops.isEmpty() && frames.values.all { it.isEmpty }

    /** What [frameIndex] draws: its own edit, or the file's. */
    fun documentFor(frameIndex: Int): EditDocument = frames[frameIndex] ?: EditDocument(ops)

    /**
     * Which scope this frame was last written in — [EditScope.ThisFrame] exactly
     * where a key exists.
     *
     * What lets an editor open in the mode the file was written in, so a second
     * sitting on frame 3 continues per-frame rather than silently going back to
     * all-frames and clearing every other frame's work on `Done`.
     */
    fun scopeOf(frameIndex: Int): EditScope =
        if (frames.containsKey(frameIndex)) EditScope.ThisFrame else EditScope.AllFrames

    /**
     * This payload with [document] recorded for [scope] — the merge `Done` writes.
     *
     * [EditScope.AllFrames] drops every key, because "every frame draws this" is
     * not true if an override wins. [EditScope.ThisFrame] leaves [ops] and every
     * other key exactly as they were, which is the loss ME-134 fixed one level up,
     * now at frame granularity: editing frame 3 in a later sitting must not drop
     * frame 1's edit.
     */
    fun with(document: EditDocument, scope: EditScope, frameIndex: Int): EditPayload =
        when (scope) {
            EditScope.AllFrames -> EditPayload(ops = document.ops)
            EditScope.ThisFrame -> copy(
                frames = frames + (frameIndex to EditDocument(document.ops))
            )
        }
}
