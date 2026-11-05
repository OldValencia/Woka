package com.woka.feature.editor.core

import androidx.compose.runtime.Stable

/**
 * What the engine's band is handed: the verbs that change the document, and whether the
 * strip is being edited or only scrubbed (ME-210, `video-editor.md` §3.4).
 *
 * **The band writes through the session exactly as an overlay does**, so undo, preview
 * against commit and history granularity stay the shell's and never become the strip's.
 * It is [EditorToolScope] without the canvas: no matrices, because the strip is not the
 * picture, and a trim handle dragged over the picture is not a design anybody wants.
 *
 * **Media-agnostic, like everything the shell hands over.** Nothing here is a clip or a
 * player; the shell learns only that a tool of [ToolDomain.Time] is open.
 *
 * **Its selection is not the canvas's.** A clip id and a markup shape id are different
 * namespaces, and one shared slot would let clicking a clip deselect an arrow — or worse,
 * hand a clip id to markup's restyle hook.
 */
@Stable
interface EditorTimelineScope {

    /** A [ToolDomain.Time] tool is open: the strip grows its handles and its actions. */
    val editing: Boolean

    /** The clip the strip has selected, or null. Never reaches the document. */
    val selectedId: String?

    fun select(id: String?)

    /** Replaces the operation in flight. Cheap; safe to call per pointer event. */
    fun preview(op: ToolOp)

    /** Folds the operation in flight in, as its own tool's cardinality says, and records one step. */
    fun commit()

    /** Drops the operation in flight — a drag that ended where it started. */
    fun cancel()

    /**
     * Selects clip [id], layer [key] — null and [NO_CLIP] for the whole film — and with it the
     * scope the look tools edit (ME-221). [step] makes it an undo step of its own; a delete or a
     * duplicate passes false and selects inside the step it is making.
     */
    fun pick(id: String?, key: Int, step: Boolean = true) = select(id)

    /** Gives layer [to] the look of layer [from] — a split's new half, a duplicate. */
    fun copyLayer(from: Int, to: Int) {}

    /** Whether layer [key] carries a look of its own, for the tile's dot. */
    fun hasOwnLook(key: Int): Boolean = false

    /** Layer [key]'s own ops — its look and its sound (ME-240). */
    fun ownOps(key: Int): List<ToolOp> = emptyList()

    /** Pastes [ops] of the kinds [taken] picks onto layer [key], as one undoable step (ME-267). */
    fun pasteLayer(key: Int, ops: List<ToolOp>, taken: (ToolOp) -> Boolean) {}

    /** The selected clip, whose own ops are in the document (ME-221); null for the whole film. */
    val openClip: Int? get() = null
}
