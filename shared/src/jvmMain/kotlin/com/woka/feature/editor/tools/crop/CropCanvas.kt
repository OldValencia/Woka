package com.woka.feature.editor.tools.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** The scrim over what the crop discards. Dark enough to read as "not kept". */
private val DISCARDED = Color.Black.copy(alpha = 0.55f)

private val RULE = Color.White.copy(alpha = 0.45f)

private const val BORDER_PX = 1f
private const val HANDLE_ARM_PX = 22f
private const val HANDLE_THICKNESS_PX = 3f

/**
 * What a crop looks like: everything outside it dimmed, a thirds grid inside, and eight
 * handles on its edges (ME-109).
 *
 * **Drawn in canvas coordinates, from a rect stored in source ones.** [sourceToCanvas]
 * is the only thing that knows the difference, which is what keeps the same rect drawn
 * correctly at any zoom and after any rotation the document carries.
 *
 * The handles are drawn at a fixed *screen* size rather than a source one — a corner
 * mark that shrank with the zoom would vanish on a large photograph and swallow a small
 * one.
 */
@Composable
internal fun CropCanvas(rect: CropRect, sourceToCanvas: Matrix, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val area = rect.canvasRect(sourceToCanvas)
        drawScrim(area)
        drawThirds(area)
        drawRect(color = Color.White, topLeft = area.topLeft, size = area.size, style = Stroke(BORDER_PX))
        drawHandles(area)
    }
}

/** The four bands around the kept region, rather than a layer with a hole cut in it. */
private fun DrawScope.drawScrim(area: Rect) {
    drawRect(DISCARDED, Offset.Zero, Size(size.width, area.top.coerceAtLeast(0f)))
    drawRect(DISCARDED, Offset(0f, area.bottom), Size(size.width, (size.height - area.bottom).coerceAtLeast(0f)))
    drawRect(DISCARDED, Offset(0f, area.top), Size(area.left.coerceAtLeast(0f), area.height))
    drawRect(DISCARDED, Offset(area.right, area.top), Size((size.width - area.right).coerceAtLeast(0f), area.height))
}

private fun DrawScope.drawThirds(area: Rect) {
    for (step in 1..2) {
        val x = area.left + area.width * step / 3f
        val y = area.top + area.height * step / 3f
        drawLine(RULE, Offset(x, area.top), Offset(x, area.bottom), BORDER_PX)
        drawLine(RULE, Offset(area.left, y), Offset(area.right, y), BORDER_PX)
    }
}

/**
 * A short arm along each edge the handle owns, so a corner reads as an angle and an
 * edge as a bar — the shape tells you which axes it will move before you drag it.
 */
private fun DrawScope.drawHandles(area: Rect) {
    // Half the stroke inwards, so a mark on the frame's own edge is drawn *inside* it.
    // At the bottom the rectangle sits against the inspector, and a handle straddling
    // that edge was half off the picture and hard to see (2026-08-22).
    val inset = HANDLE_THICKNESS_PX / 2f
    val arm = HANDLE_ARM_PX

    CropHandle.resizing.forEach { handle ->
        val x = when {
            handle.movesLeft -> area.left + inset
            handle.movesRight -> area.right - inset
            else -> area.center.x
        }
        val y = when {
            handle.movesTop -> area.top + inset
            handle.movesBottom -> area.bottom - inset
            else -> area.center.y
        }
        // A corner's arms run inwards from it; an edge's mark is centred on its middle.
        val alongY = when {
            handle.movesTop -> y to y + arm
            handle.movesBottom -> y - arm to y
            else -> y - arm / 2 to y + arm / 2
        }
        val alongX = when {
            handle.movesLeft -> x to x + arm
            handle.movesRight -> x - arm to x
            else -> x - arm / 2 to x + arm / 2
        }
        if (handle.movesLeft || handle.movesRight) {
            drawLine(Color.White, Offset(x, alongY.first), Offset(x, alongY.second), HANDLE_THICKNESS_PX)
        }
        if (handle.movesTop || handle.movesBottom) {
            drawLine(Color.White, Offset(alongX.first, y), Offset(alongX.second, y), HANDLE_THICKNESS_PX)
        }
    }
}

/** The rect as the canvas draws it right now. */
internal fun CropRect.canvasRect(sourceToCanvas: Matrix): Rect {
    val topLeft = sourceToCanvas.map(Offset(left.toFloat(), top.toFloat()))
    val bottomRight = sourceToCanvas.map(Offset(right.toFloat(), bottom.toFloat()))
    return Rect(topLeft, bottomRight)
}
