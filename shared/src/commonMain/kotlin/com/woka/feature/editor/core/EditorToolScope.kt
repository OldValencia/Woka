package com.woka.feature.editor.core

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize

/**
 * What a [CanvasTool]'s overlay is handed: the document, the two verbs that change
 * it, and the geometry needed to turn a pointer position into a source coordinate.
 *
 * **Media-agnostic on purpose.** Nothing here is a bitmap or a player, so a video
 * trim overlay takes the same scope an image crop overlay does — which is the
 * claim §3.3 makes about the shell being inherited rather than reimplemented.
 *
 * **Selection is not in the document** (§3.1). [selectedId] and [select] live here,
 * in the tool's scope, precisely so that clicking a markup shape never produces an
 * undo step. The document records what was drawn; this records what is highlighted
 * right now, and closing the tool forgets it.
 */
@Stable
interface EditorToolScope {

    /** The live document, pending operation included. */
    val document: EditDocument

    /**
     * Size of what the operations address, in **source** pixels — never the canvas
     * or the proxy. An overlay that measured itself against the drawn size would
     * produce markup that moves when the window is resized.
     */
    val sourceSize: IntSize

    /** Canvas coordinates to source coordinates: what a pointer position means. */
    val canvasToSource: Matrix

    /** The inverse: where a stored shape is drawn right now. */
    val sourceToCanvas: Matrix

    /** The shape the overlay is manipulating, or null. Never reaches the document. */
    val selectedId: String?

    fun select(id: String?)

    /** Replaces the operation in flight. Cheap; safe to call per pointer event. */
    fun preview(op: ToolOp)

    /** Folds the operation in flight in and records one undo step. */
    fun commit()

    /**
     * Drops the operation in flight without applying it — the escape hatch an abandoned
     * gesture needs and a finished one never uses (ME-145).
     *
     * `ParamWriter` has had this since ME-102; an overlay had no way to reach it, so a
     * text edit dismissed with `Esc` could only be undone by *committing* the state it was
     * abandoning and then undoing that. One history entry for changing nothing.
     */
    fun cancel()

    /**
     * "This tool is taking typed input, leave the keyboard alone" (ME-145).
     *
     * The editor previews every key at the window root before focus reaches anything
     * (`EditorKeys`), which is what lets `Esc` and `Delete` work over a canvas nobody has
     * clicked. A text field inside an overlay is the one thing that breaks under: with the
     * preview handler in place, `Backspace` deletes the shape being typed into instead of
     * a character. Set while a field is open, cleared when it closes, and cleared for the
     * tool by the shell on dispose.
     */
    fun captureKeys(captured: Boolean)
}
