package com.woka.feature.editor.tools.markup

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditorToolScope

/** How near counts as grabbing, in **screen** pixels — the same target at any zoom. */
private const val GRAB_PX = 14f

/**
 * Every press the markup overlay answers, split out of `MarkupOverlay` under the size
 * mandate when the caret arrived (ME-145) — the same shape `editorCanvasGestures` takes
 * one layer down, and the same seam: a `Modifier` that recognises, and a composable that
 * draws.
 *
 * **[scope] and [editing] are read as functions, never captured.** `pointerInput` keeps
 * the lambda it was given until [frame] changes, so a scope captured by value would be
 * the one from the composition the gesture *started* in, and every event of a drag would
 * measure against the document as it was before it. That is the bug Crop's overlay
 * shipped with, found by hand 2026-08-21.
 */
internal fun Modifier.markupGestures(
    frame: IntSize,
    scope: () -> EditorToolScope,
    selectOnly: () -> Boolean,
    editing: () -> MarkupTextEdit?,
    onEdit: (MarkupTextEdit?) -> Unit
): Modifier = pointerInput(frame) {
    awaitEachGesture {
        // **Not `detectDragGestures`.** The overlay covers the whole picture, so a press
        // that lands on nothing of ours is left unconsumed and the host's own drag
        // handler pans the canvas underneath.
        val down = awaitFirstDown(requireUnconsumed = true)
        val live = scope()

        // A press anywhere else finishes an open edit and does nothing more. The field's
        // own blur handler cannot see this one: the canvas beneath it is not focusable,
        // so clicking away never moves focus off it.
        editing()?.let { open ->
            onEdit(null)
            live.commitTextEdit(open)
            down.consume()
            return@awaitEachGesture
        }

        // Ctrl is pan, always — even over a shape, and even when the picture is covered
        // in them.
        if (currentEvent.keyboardModifiers.isCtrlPressed) return@awaitEachGesture

        val from = live.canvasToSource.sourcePoint(down.position)
        val shapes = live.document.markupShapes()
        val wasSelected = live.selectedId
        val held = shapes.firstOrNull { it.id == wasSelected }
        val gesture =
            markupGestureAt(shapes, held, from.x, from.y, live.sourcePixels(GRAB_PX))
        val typed = live.document.markupKind() == MarkupKind.Text

        // A press on empty picture with something selected only deselects: a stray click
        // must not mint a shape, and the press stays unconsumed so it can still pan.
        //
        // **With no tool open the pen is off** (ME-149), so the same branch is taken
        // whether or not anything was selected: nothing may be created there, and leaving
        // the press unconsumed is what still lets it pan the picture.
        val idle = selectOnly()
        if (gesture is MarkupGesture.Draw && (idle || wasSelected != null)) {
            live.select(null)
            return@awaitEachGesture
        }
        if (gesture is MarkupGesture.Move) live.select(gesture.id)
        down.consume()

        var drawn: String? = null
        var wrote = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            val step = change.positionChange()
            // A press that never moves writes nothing at all — which is what makes a
            // click on a shape select it without an undo step, and what leaves the click
            // free to mean "type here".
            if (step != Offset.Zero) {
                val now = scope()
                val at = now.canvasToSource.sourcePoint(change.position)
                when (gesture) {
                    is MarkupGesture.Resize -> {
                        now.resize(gesture, at)
                        wrote = true
                    }
                    is MarkupGesture.Move -> {
                        now.move(gesture.id, now.canvasToSource.mapDelta(step))
                        wrote = true
                    }
                    // **Type is placed, not dragged out.** Without this a drag with the
                    // text pen out mints an empty shape and an undo step for a picture
                    // that did not change.
                    MarkupGesture.Draw -> if (!typed) {
                        drawn = now.draw(drawn, from, at)
                        wrote = true
                    }
                }
            }
            change.consume()
        }

        if (wrote) {
            scope().commit()
            return@awaitEachGesture
        }
        // And no caret with no tool open (ME-149): a second click on a caption may select
        // it and nothing more, since there is no inspector to type alongside.
        if (idle) return@awaitEachGesture

        // A press that never moved: the click that opens a caret on empty picture, or the
        // second click that reopens a caption already selected.
        scope().textEditAt(gesture, from, wasSelected)?.let { shape ->
            val existed = scope().holdsShape(shape)
            // Into the list at once, and selected, so the inspector's rows describe it
            // from the first keystroke rather than from the commit.
            scope().previewTextEdit(shape, shape.text)
            scope().select(shape.id)
            onEdit(MarkupTextEdit(shape, shape.text, existed))
        }
    }
}
