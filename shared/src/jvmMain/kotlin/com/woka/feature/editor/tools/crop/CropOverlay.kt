package com.woka.feature.editor.tools.crop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.testTag
import com.woka.feature.editor.core.EditorToolScope
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the overlay's gestures land, for the UI tests. */
internal const val CROP_OVERLAY_TAG = "crop-overlay"

/**
 * How near a handle counts as grabbing it, in **screen** pixels. Converted to source
 * through the canvas scale, so the target stays the same size on screen at any zoom.
 */
private const val GRAB_PX = 18f

/**
 * And wider at a corner. A corner is two edges' worth of intent in one point, the
 * hardest thing on screen to hit, and the bottom two sit right where the picture meets
 * the inspector below it.
 */
private const val CORNER_GRAB_PX = 40f

/**
 * The crop rectangle, drawn and dragged (ME-109).
 *
 * **One undo step per gesture** (§3.1): every pointer event calls `scope.preview`, and
 * `scope.commit` runs once on release. Committing per event would make undoing a drag
 * take as many presses as it took frames.
 *
 * **The scope is read through `rememberUpdatedState`, and that is not a nicety.**
 * `pointerInput` keeps the lambda it was given until its keys change, so a scope
 * captured directly would be the one from the composition the gesture *started* in —
 * every event of a drag would then measure from the rect as it was before the drag,
 * and the rectangle would jitter around its original size instead of resizing. Found
 * by hand, 2026-08-21.
 */
@Composable
internal fun CropOverlay(scope: EditorToolScope) {
    val current by rememberUpdatedState(scope)
    val frame = scope.sourceSize
    val rect = scope.document.cropRectIn(frame)
    val ratio = scope.document.cropRatioIn(frame)

    CropAspectEffect(current, frame, ratio)

    CropCanvas(
        rect = rect,
        sourceToCanvas = scope.sourceToCanvas,
        modifier = Modifier
            .fillMaxSize()
            .testTag(CROP_OVERLAY_TAG)
            .pointerInput(frame) {
                awaitEachGesture {
                    // **Not `detectDragGestures`.** That takes every drag on the
                    // overlay, and the overlay covers the whole picture — so the canvas
                    // underneath could never be panned again. Here the press is left
                    // unconsumed when it lands on nothing of ours, and the host's own
                    // drag handler picks it up (2026-08-22).
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val live = current
                    val (x, y) = live.canvasToSource.toSource(down.position)
                    val held = live.document.cropRectIn(frame)
                    val handle = held.handleAt(
                        x = x,
                        y = y,
                        tolerance = live.sourcePixels(GRAB_PX),
                        cornerTolerance = live.sourcePixels(CORNER_GRAB_PX)
                    )
                    // Ctrl is "pan the picture", even over the rectangle: with the rect
                    // at the whole frame there would otherwise be nowhere to grab.
                    if (handle == null || currentEvent.keyboardModifiers.isCtrlPressed) {
                        return@awaitEachGesture
                    }
                    down.consume()

                    var carried = Offset.Zero
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val step = live.canvasToSource.mapDelta(change.positionChange()) + carried
                        val dx = step.x.roundToInt()
                        val dy = step.y.roundToInt()
                        carried = Offset(step.x - dx, step.y - dy)
                        if (dx != 0 || dy != 0) {
                            val now = current
                            val ratio = now.document.cropRatioIn(frame)
                            val next = now.document.cropRectIn(frame)
                                .resizedBy(handle, dx, dy, frame, ratio)
                            now.preview(now.document.cropOpWith(next))
                            moved = true
                        }
                        change.consume()
                    }
                    if (moved) current.commit()
                }
            }
    )
}

/**
 * Choosing a ratio reshapes the rectangle **there and then**.
 *
 * Without this the chip lit up and nothing moved: the aspect was stored and only
 * consulted the next time a handle was dragged, which is indistinguishable from a
 * control that does not work. `Free` reshapes nothing — it is the absence of a lock,
 * not a shape of its own.
 */
@Composable
private fun CropAspectEffect(scope: EditorToolScope, frame: IntSize, ratio: Float?) {
    LaunchedEffect(ratio, frame) {
        if (ratio == null) return@LaunchedEffect
        val rect = scope.document.cropRectIn(frame)
        val fitted = rect.fittedTo(ratio, frame)
        if (fitted == rect) return@LaunchedEffect
        scope.preview(scope.document.cropOpWith(fitted))
        scope.commit()
    }
}

/**
 * [screen] pixels as source ones, at the zoom the canvas is currently drawn at.
 *
 * **Absolute**, because the mapping can be mirrored: with a flip in the preview the
 * difference along x is *negative*, and a distance that came out negative was clamped
 * to one — which is a handle you have to hit within a single source pixel. That is what
 * "the corners cannot be grabbed" turned out to be on a flipped picture (2026-08-22).
 */
private fun EditorToolScope.sourcePixels(screen: Float): Int {
    val origin = canvasToSource.map(Offset.Zero)
    val along = canvasToSource.map(Offset(screen, 0f))
    return abs(along.x - origin.x).roundToInt().coerceAtLeast(1)
}

/**
 * A **direction and distance**, not a position: the translation in the matrix must not
 * be applied to a delta, or every drag would be offset by wherever the picture happens
 * to sit on the canvas.
 */
private fun Matrix.mapDelta(delta: Offset): Offset =
    map(delta) - map(Offset.Zero)
