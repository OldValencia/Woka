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
    val frames: Map<Int, EditDocument> = emptyMap(),
    /**
     * Whether a frame's own edit **composes with** [ops] or replaces it (ME-175).
     *
     * It exists because files already carry the older meaning. Until 2026-09-02 a key
     * won outright — "frame 3 draws this *instead*" — and reading those files under the
     * new rule would draw both and change a photograph somebody edited last week.
     * Absent is the old rule, present is the new, and `editorJson` omits defaults so
     * nothing is written into a file that does not need it.
     *
     * **Not a version number.** A version would have to be interpreted; this names the
     * one thing that actually differs, and when the last legacy file is gone the flag
     * can be deleted without anything having to be decoded to find out what it meant.
     */
    val layered: Boolean = false
) {

    /**
     * Whether this payload folds to nothing at all, keys included.
     *
     * §5.2's rule reaches frame granularity through this: a payload that draws
     * nothing removes the recipe, exactly as Revert does from the other direction.
     */
    val isEmpty: Boolean get() = ops.isEmpty() && frames.values.all { it.isEmpty }

    /**
     * What [frameIndex] draws: the file's edit, and then its own on top (ME-175).
     *
     * **The order is the point.** The frame's ops come second, so where the two touch
     * the same thing the frame's is the later word — and the fold sorts by domain
     * regardless, so a base adjustment and a per-frame one still apply in the order §3.4
     * demands rather than in the order they were merged.
     *
     * A frame with no key of its own draws the base, which is the same document under
     * either rule: an absent key adds nothing.
     *
     * [layered] false is the older meaning, where a key won outright. It is kept for the
     * files that carry it and for nothing else.
     */
    fun documentFor(frameIndex: Int): EditDocument {
        val own = frames[frameIndex] ?: return EditDocument(ops)
        if (!layered) return own
        return EditDocument(ops + own.ops)
    }

    /**
     * Which scope this frame was last written in — [EditScope.SingleFrame] exactly
     * where a key exists.
     *
     * What lets an editor open in the mode the file was written in, so a second
     * sitting on frame 3 continues per-frame rather than silently going back to
     * all-frames and clearing every other frame's work on `Done`.
     */
    fun scopeOf(frameIndex: Int): EditScope =
        if (frames.containsKey(frameIndex)) EditScope.SingleFrame else EditScope.AllFrames

    /**
     * This payload with [document] recorded for [scope] — the merge `Done` writes.
     *
     * **Neither scope discards the other any more** (ME-175). [EditScope.AllFrames] used
     * to drop every key, on the reading that "every frame draws this" is false wherever
     * an override wins; under layering an override does not win, it adds, so the two are
     * a base and its deltas and both survive one `Done`. That is what lets a sitting edit
     * frame 0 for itself and frame 2 for everything without losing the first.
     *
     * **[EditScope.SingleFrame] stores the delta, not the document.** The editor opens on
     * the *composed* document, so writing it whole would store the base a second time and
     * apply it twice — a doubled exposure on the one frame somebody worked on. The base
     * is subtracted here, where both halves are in hand.
     *
     * Writing through this always sets [layered]: a payload the current build wrote means
     * the current rule, whatever it meant before.
     */
    fun with(document: EditDocument, scope: EditScope, frameIndex: Int): EditPayload =
        when (scope) {
            EditScope.AllFrames -> copy(ops = document.ops, layered = true)
            EditScope.SingleFrame -> copy(
                frames = frames + (frameIndex to EditDocument(document.ops - ops.toSet())),
                layered = true
            )
        }
}
