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
}
