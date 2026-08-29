package com.woka.feature.editor.tools.markup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The handle, in **screen** pixels: a mark that shrank with the zoom would vanish on a
 * large photograph and swallow a small one (`CropCanvas` makes the same choice).
 */
private const val HANDLE_RADIUS_PX = 6f

private const val HANDLE_BORDER_PX = 1.5f

/** How far the dashed box sits off a freehand stroke, so it frames rather than touches. */
private const val BOX_INSET_PX = 6f

/**
 * What is selected, drawn over the picture (ME-114).
 *
 * **The shapes themselves are not drawn here**, which is a deliberate departure from the
 * ticket's step 2: markup leaves `previewsThroughOverlay` false, so the fold applies the
 * shape list to the canvas underneath — including the operation in flight, since the
 * fold folds `pending` in exactly like a committed one. Drawing the list again here
 * would put a second copy of every stroke on top of the first, at a slightly different
 * scale, and translucency would double up on every overlap.
 *
 * So this draws the one thing the fold cannot know about: the selection, which lives in
 * the tool's scope and never reaches the document (§3.1).
 */
@Composable
internal fun MarkupCanvas(
    selected: MarkupShape?,
    sourceToCanvas: Matrix,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (selected == null) return@Canvas

        val handles = selected.handles().map { sourceToCanvas.map(Offset(it.x, it.y)) }
        // **The box, and for a caption the dots as well** (ME-148). A freehand stroke has
        // no handle to pull on, and a selection nothing marks is one the `Delete` key
        // appears to ignore; a caption has four corners *and* wants the frame that says
        // what they will do to it. An arrow and a drawn box keep their dots alone — their
        // outline is already the thing being resized.
        if (selected.framed) {
            drawSelectionBox(selected.points(sourceToCanvas))
        }
        handles.forEach { at -> drawHandle(at) }
    }
}

/**
 * White, with a dark ring: the two together read on a photograph of a snowfield and on
 * one of a night sky, which a single colour cannot.
 */
private fun DrawScope.drawHandle(at: Offset) {
    drawCircle(Color.White, radius = HANDLE_RADIUS_PX, center = at)
    drawCircle(
        color = Color.Black.copy(alpha = 0.45f),
        radius = HANDLE_RADIUS_PX,
        center = at,
        style = Stroke(HANDLE_BORDER_PX)
    )
}

private fun DrawScope.drawSelectionBox(points: List<Offset>) {
    if (points.isEmpty()) return

    val box = Rect(
        left = points.minOf { it.x } - BOX_INSET_PX,
        top = points.minOf { it.y } - BOX_INSET_PX,
        right = points.maxOf { it.x } + BOX_INSET_PX,
        bottom = points.maxOf { it.y } + BOX_INSET_PX
    )
    drawRect(
        color = Color.White.copy(alpha = 0.8f),
        topLeft = box.topLeft,
        size = box.size,
        style = Stroke(
            width = HANDLE_BORDER_PX,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
    )
}

/**
 * Whether this shape is marked by a dashed frame as well as by its handles.
 *
 * A freehand stroke because it has no handles at all, and a caption because its handles
 * scale it rather than reshape it — the frame is what says the corners belong to the block
 * as a whole (ME-148). The three whose own outline is what a corner moves get dots alone.
 */
private val MarkupShape.framed: Boolean
    get() = this is MarkupShape.Freehand || this is MarkupShape.Text

/** Every point of the shape, where the canvas is drawing it right now. */
private fun MarkupShape.points(sourceToCanvas: Matrix): List<Offset> {
    val source = when (this) {
        is MarkupShape.Freehand -> points
        else -> handles()
    }
    return source.map { sourceToCanvas.map(Offset(it.x, it.y)) }
}
