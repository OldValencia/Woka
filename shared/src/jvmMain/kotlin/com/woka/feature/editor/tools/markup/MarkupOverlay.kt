package com.woka.feature.editor.tools.markup

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import com.woka.feature.editor.core.EditorToolScope

/** Where the overlay's gestures land, for the UI tests. */
internal const val MARKUP_OVERLAY_TAG = "markup-overlay"

/** How near counts as grabbing, in **screen** pixels — the same target at any zoom. */
private const val GRAB_PX = 14f

/**
 * Drawing, selecting, moving and resizing on the canvas (ME-114).
 *
 * **One undo step per gesture** (§3.1): every pointer event calls `scope.preview`, and
 * `scope.commit` runs once on release. Adding, moving and resizing are all "preview a
 * different shape list" — the mechanism Crop already uses, and the whole reason ME-113
 * made this tool `Single` with one operation carrying the list.
 *
 * **The scope is read through `rememberUpdatedState`.** `pointerInput` keeps the lambda
 * it was given until its keys change, so a scope captured directly would be the one from
 * the composition the gesture *started* in — every event would then measure against the
 * document as it was before the drag. That is the bug Crop's overlay shipped with, found
 * by hand 2026-08-21.
 */
@Composable
internal fun MarkupOverlay(scope: EditorToolScope) {
    val current by rememberUpdatedState(scope)
    val frame = scope.sourceSize
    val selected = scope.document.markupShapes().firstOrNull { it.id == scope.selectedId }

    MarkupCanvas(
        selected = selected,
        sourceToCanvas = scope.sourceToCanvas,
        modifier = Modifier
            .fillMaxSize()
            .testTag(MARKUP_OVERLAY_TAG)
            .pointerInput(frame) {
                awaitEachGesture {
                    // **Not `detectDragGestures`.** The overlay covers the whole picture,
                    // so a press that lands on nothing of ours is left unconsumed and the
                    // host's own drag handler pans the canvas underneath.
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val live = current
                    // Ctrl is pan, always — even over a shape, and even when the picture
                    // is covered in them.
                    if (currentEvent.keyboardModifiers.isCtrlPressed) return@awaitEachGesture

                    val from = live.canvasToSource.sourcePoint(down.position)
                    val shapes = live.document.markupShapes()
                    val held = shapes.firstOrNull { it.id == live.selectedId }
                    val gesture =
                        markupGestureAt(shapes, held, from.x, from.y, live.sourcePixels(GRAB_PX))

                    // A press on empty picture with something selected only deselects: a
                    // stray click must not mint a shape, and the press stays unconsumed
                    // so it can still pan.
                    if (gesture is MarkupGesture.Draw && live.selectedId != null) {
                        live.select(null)
                        return@awaitEachGesture
                    }
                    if (gesture is MarkupGesture.Move) live.select(gesture.id)
                    down.consume()

                    var drawn: String? = null
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val step = change.positionChange()
                        // A press that never moves writes nothing at all — which is what
                        // makes a click on a shape select it without an undo step.
                        if (step != Offset.Zero) {
                            val now = current
                            val at = now.canvasToSource.sourcePoint(change.position)
                            when (gesture) {
                                is MarkupGesture.Resize -> now.resize(gesture, at)
                                is MarkupGesture.Move ->
                                    now.move(gesture.id, now.canvasToSource.mapDelta(step))
                                MarkupGesture.Draw -> drawn = now.draw(drawn, from, at)
                            }
                            moved = true
                        }
                        change.consume()
                    }
                    if (moved) current.commit()
                }
            }
    )
}
