package com.woka.feature.editor.tools.markup

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolScope
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import java.util.UUID

/**
 * Reading markup out of a document and writing it back (ME-114) — the twin of
 * `CropState`, and for the same reason: "what the document currently says" is answered
 * in one place rather than in every branch of a gesture.
 *
 * The operation in flight wins over the committed one, because a drag in flight is what
 * the user is looking at; `Single` cardinality means there is never more than one
 * committed markup operation to choose between.
 */

/** The markup in flight, then the committed one, then nothing. */
fun EditDocument.markupOp(): ToolOp? =
    pending?.takeIf { it.toolId == MarkupTool.manifest.id }
        ?: opsOf(MarkupTool.manifest.id).lastOrNull()

/**
 * The parameters the tool's controls currently describe — `paramsOf` in this file's
 * terms, which the overlay cannot call: that one lives in the shell and takes a tool.
 */
private fun EditDocument.markupParams(): ParamSet = markupOp()?.params ?: MarkupTool.defaults()

fun EditDocument.markupShapes(): List<MarkupShape> = markupParams()[MarkupTool.Shapes]

/**
 * The pen the next shape is drawn with — the inspector's three rows, read at the moment
 * the stroke starts and copied into it. Restyling the pen afterwards changes nothing
 * already drawn, which is what makes the pen a *setting* rather than a selection.
 */
fun EditDocument.markupKind(): MarkupKind = markupParams()[MarkupTool.Kind]

fun EditDocument.markupColor(): Long = markupParams()[MarkupTool.Color]

fun EditDocument.markupWidth(): Float = markupParams()[MarkupTool.Width]

/**
 * The operation that records [shapes], carrying every other parameter the document
 * already held — a drag must not reset the pen the inspector just set.
 *
 * From the tool's defaults when there is nothing yet, never from an empty set: a partial
 * `ParamSet` is one the inspector completes on the next composition, and that write is
 * an undo step nobody asked for (`CropState`).
 */
fun EditDocument.markupOpWith(shapes: List<MarkupShape>): ToolOp = ToolOp(
    toolId = MarkupTool.manifest.id,
    params = markupParams().with(MarkupTool.Shapes, shapes)
)

/**
 * Removes what is selected, as **one** undo step — preview the shorter list, commit,
 * forget the selection (ME-114).
 *
 * False when there is nothing selected, or when the selection names a shape the document
 * no longer holds: the id outlives an undo that took the shape away, and deleting
 * "nothing" must not mint a history entry that changes the picture not at all.
 */
fun EditorToolScope.deleteSelectedShape(): Boolean {
    val id = selectedId ?: return false
    val shapes = document.markupShapes()
    if (shapes.none { it.id == id }) return false

    preview(document.markupOpWith(shapes.filterNot { it.id == id }))
    commit()
    select(null)
    return true
}

/**
 * An id for a new shape: eight hex characters of a random UUID.
 *
 * Eight rather than the whole thing because every one of these is written into the
 * user's file — a document with twenty marks would otherwise carry 720 bytes of
 * identifier. Collision inside one picture's shape list is what matters here, and at
 * 4 × 10⁹ values with dozens of shapes it is not a risk worth 28 more characters each.
 */
internal fun newShapeId(): String = UUID.randomUUID().toString().take(8)
