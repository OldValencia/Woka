package com.woka.feature.editor.tools.markup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.woka.feature.editor.tools.markup.MarkupShape.Arrow
import com.woka.feature.editor.tools.markup.MarkupShape.Ellipse
import com.woka.feature.editor.tools.markup.MarkupShape.Freehand
import com.woka.feature.editor.tools.markup.MarkupShape.Rectangle
import com.woka.feature.editor.tools.markup.MarkupShape.Text
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The head's length, as a multiple of the stroke — an arrow keeps its shape at any width. */
private const val HEAD_LENGTH = 4f

/** Half the angle the head opens by, in radians: 30°, which reads as an arrow rather than a tick. */
private const val HEAD_SPREAD = 0.52f

/**
 * **The single draw routine every path shares** (ME-113).
 *
 * The editor's canvas, the fold's `RasterStage.Paint` and the save all call this one
 * function, which is what makes "markup is rasterised at source resolution" fall out of
 * the design rather than being arranged: the recipe is drawn in source coordinates in
 * all three places, so the only difference between a preview stroke and a saved one is
 * the scale of the surface underneath it (§3.2).
 *
 * It draws in **source pixels** and applies no transform of its own. A caller that has
 * scaled its `DrawScope` gets a scaled stroke, which is exactly what the preview wants;
 * a caller that has not gets the file's own pixels.
 */
fun DrawScope.drawMarkup(shape: MarkupShape) {
    val color = Color(shape.color)
    // Round everywhere: a stroke ending in a square cap looks cut off where two freehand
    // segments meet at a sharp angle, and a mitre joint spikes there instead.
    val stroke = Stroke(width = shape.width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (shape) {
        is Arrow -> drawArrow(shape, color)
        is Rectangle -> drawRect(
            color = color,
            topLeft = topLeftOf(shape.from, shape.to),
            size = sizeOf(shape.from, shape.to),
            style = stroke
        )
        is Ellipse -> drawOval(
            color = color,
            topLeft = topLeftOf(shape.from, shape.to),
            size = sizeOf(shape.from, shape.to),
            style = stroke
        )
        is Freehand -> drawFreehand(shape, color, stroke)
        // The only branch that owns a font, and therefore the only one that lives in a
        // file of its own — see `MarkupText`.
        is Text -> drawMarkupText(shape, color)
    }
}

/**
 * The shaft, plus two strokes back from the tip.
 *
 * Two lines rather than a filled triangle: the head then shares the shaft's width, cap
 * and colour by construction, so it stays proportional at 1 px and at 64 without a
 * second size rule to keep in step.
 */
private fun DrawScope.drawArrow(shape: Arrow, color: Color) {
    val from = Offset(shape.from.x, shape.from.y)
    val to = Offset(shape.to.x, shape.to.y)
    drawLine(color, from, to, strokeWidth = shape.width, cap = StrokeCap.Round)

    val angle = atan2(to.y - from.y, to.x - from.x)
    // Never longer than the arrow itself: a short drag would otherwise draw a head
    // bigger than the shaft it sits on.
    val length = min(shape.width * HEAD_LENGTH, (to - from).getDistance())
    listOf(angle + HEAD_SPREAD, angle - HEAD_SPREAD).forEach { barb ->
        drawLine(
            color = color,
            start = to,
            end = Offset(to.x - cos(barb) * length, to.y - sin(barb) * length),
            strokeWidth = shape.width,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawFreehand(shape: Freehand, color: Color, stroke: Stroke) {
    val points = shape.points
    if (points.isEmpty()) return

    // A stroke that was tapped rather than dragged: a one-point path draws nothing at
    // all, so the dot the user saw under the pointer would vanish on release.
    val first = points.first()
    if (points.size == 1) {
        drawCircle(color, radius = shape.width / 2f, center = Offset(first.x, first.y))
        return
    }

    val path = Path()
    path.moveTo(first.x, first.y)
    points.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
    drawPath(path, color, style = stroke)
}

/**
 * A box is stored as the two points it was dragged between, in that order, so either
 * corner may be the smaller one — Skia wants the top-left and a positive size.
 */
private fun topLeftOf(from: MarkupPoint, to: MarkupPoint) =
    Offset(min(from.x, to.x), min(from.y, to.y))

private fun sizeOf(from: MarkupPoint, to: MarkupPoint) =
    Size(abs(to.x - from.x), abs(to.y - from.y))
