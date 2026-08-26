package com.woka.feature.editor.tools.markup

import com.woka.feature.editor.core.EditorToolScope

/**
 * What a text edit does to the document (ME-145) — the twin of `MarkupEdits`, and split
 * from it for the reason that file states about itself: above, a pointer loop that decides
 * *when*; here, the functions that decide *what*.
 *
 * **The caption is in the document from the moment the caret is placed**, previewed and
 * not committed, and every keystroke previews it again. That reverses how this shipped
 * first — the string lived in the field until it was finished — and here is what the
 * reversal buys (reported by hand 2026-08-26):
 *
 * - **The style rows reach it.** `restyleSelected` finds a shape by id in the list, and a
 *   caption that was not in the list yet could not be found: choosing a size or a colour
 *   with the caret open moved the pen and left the caption at whatever it was minted with.
 *   Worse, clicking a row is what *blurs* the field, so the write landed **before** the
 *   commit and with nothing selected — the row appeared to do nothing at all.
 * - **The canvas draws it**, through the fold, at the size and colour it will be saved
 *   with. The field above it is transparent (`MarkupTextField`) and contributes the caret
 *   and nothing else, so there is no second copy to keep in step.
 *
 * A preview is not an undo step (§3.1), so a hundred keystrokes still cost one entry.
 */

/**
 * What a press means when the text pen is out, or when a caption is clicked a second
 * time — null when this press is not the start of an edit at all.
 */
internal fun EditorToolScope.textEditAt(
    gesture: MarkupGesture,
    at: MarkupPoint,
    wasSelected: String?
): MarkupShape.Text? {
    if (gesture is MarkupGesture.Move) {
        // The **second** click opens it: the first one selected it, which is what leaves a
        // caption draggable before it is editable.
        if (gesture.id != wasSelected) return null
        return document.markupShapes().firstOrNull { it.id == gesture.id } as? MarkupShape.Text
    }
    if (gesture !is MarkupGesture.Draw) return null
    if (document.markupKind() != MarkupKind.Text) return null

    return MarkupShape.Text(
        id = newShapeId(),
        color = document.markupColor(),
        width = document.markupTextSize(),
        at = at,
        text = "",
        // Inherited from the picture, never typed (ME-146) — and only here, on a caption
        // being minted. The reopen branch above returns the stored shape untouched, or a
        // second click would overwrite a wrap a corner drag had scaled.
        wrap = wrapAt(sourceSize.width, at.x),
        fill = document.markupFill(),
        corner = document.markupCorner()
    )
}

/** True when [shape] is already part of the document rather than a caret just placed. */
internal fun EditorToolScope.holdsShape(shape: MarkupShape.Text): Boolean =
    document.markupShapes().any { it.id == shape.id }

/**
 * The caption as it stands, previewed into the list — added at the end if it is new, and
 * otherwise **updated in place**, so re-opening one does not move it in front of the marks
 * it was drawn behind.
 *
 * The entry it copies from is the one in the *document*, never the shape the edit began
 * with: a size or a colour chosen while the caret is open lives there, and rebuilding from
 * the original would quietly undo it on the next keystroke.
 */
internal fun EditorToolScope.previewTextEdit(shape: MarkupShape.Text, text: String) {
    val shapes = document.markupShapes()
    val next = if (shapes.any { it.id == shape.id }) {
        shapes.map { if (it.id == shape.id) it.withText(text) else it }
    } else {
        shapes + shape.copy(text = text)
    }
    preview(document.markupOpWith(next))
}

/**
 * The edit, finished.
 *
 * A caption with nothing in it is not a mark: a caret placed and abandoned is dropped
 * whole, and an existing one emptied is deleted. Everything else commits — **including an
 * edit that changed only the size**, which is why this does not compare strings and
 * quietly cancel.
 */
internal fun EditorToolScope.commitTextEdit(edit: MarkupTextEdit) {
    val blank = edit.text.isBlank()
    if (blank && !edit.existed) {
        // Never committed, so dropping what is in flight leaves no trace at all.
        cancel()
        select(null)
        return
    }
    if (blank) {
        val kept = document.markupShapes().filterNot { it.id == edit.shape.id }
        preview(document.markupOpWith(kept))
    }
    commit()
    select(edit.shape.id.takeIf { !blank })
}

/**
 * The edit carrying the caption **as the document has it now** (reported by hand
 * 2026-08-27).
 *
 * [MarkupTextEdit.shape] is the caption as it was when the caret opened, and it stays that
 * way on purpose — `commitTextEdit` compares against it to tell an emptied caption from an
 * abandoned caret. But the *field* must not draw from it: choosing a size or a colour while
 * the caret is open restyles the entry in the list, and a caret still measured from the
 * opening shape stays the height it started at while the text under it changes.
 *
 * Falls back to the original when the list no longer holds it, which is the frame between
 * an undo taking the caption away and the edit closing.
 */
internal fun EditorToolScope.liveEdit(edit: MarkupTextEdit): MarkupTextEdit {
    val live = document.markupShapes()
        .firstOrNull { it.id == edit.shape.id } as? MarkupShape.Text
        ?: return edit
    return edit.copy(shape = live)
}

/** `Esc`: the pending edit is dropped and the committed list is what stands. */
internal fun EditorToolScope.abandonTextEdit() {
    cancel()
    select(null)
}

/** The same caption carrying a different string, whatever else has been done to it. */
private fun MarkupShape.withText(text: String): MarkupShape =
    if (this is MarkupShape.Text) copy(text = text) else this
