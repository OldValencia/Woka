package com.woka.feature.editor.tools.markup

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * How far a point is from a shape's outline — the arithmetic `MarkupGeometry.hitTest`
 * branches over (ME-113).
 *
 * Split from `MarkupGeometry` because that file reached the size mandate, along the seam
 * that was already there: the four operations a gesture calls, and the distance rules
 * they share. Nothing here knows what a `MarkupShape` is.
 */

/** The nearest edge of a closed ring of points — a box's four, walked back to the first. */
internal fun distanceToOutline(x: Float, y: Float, ring: List<MarkupPoint>): Float =
    (ring + ring.first())
        .zipWithNext()
        .minOf { (a, b) -> distanceToSegment(x, y, a, b) }

internal fun distanceToSegment(x: Float, y: Float, a: MarkupPoint, b: MarkupPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    // A zero-length segment is a point, and projecting onto it divides by zero — a
    // freehand stroke that was tapped rather than dragged is exactly that.
    if (lengthSquared == 0f) return hypot(x - a.x, y - a.y)

    val t = (((x - a.x) * dx + (y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(x - (a.x + t * dx), y - (a.y + t * dy))
}

/**
 * Distance to the ellipse inscribed in the box [from]–[to], **approximated** by the
 * radial projection: the point is measured against where the ray from the centre through
 * it crosses the outline.
 *
 * That is not the true nearest point on an ellipse — which has no closed form and costs
 * an iteration — and near the flat flank of a very eccentric oval it reads slightly
 * large. This is a grab radius, so being a pixel conservative there is invisible; an
 * iterative solve per pointer event would not be.
 */
internal fun distanceToEllipse(x: Float, y: Float, from: MarkupPoint, to: MarkupPoint): Float {
    val cx = (from.x + to.x) / 2f
    val cy = (from.y + to.y) / 2f
    val rx = abs(to.x - from.x) / 2f
    val ry = abs(to.y - from.y) / 2f
    // A collapsed axis makes an ellipse a line, and the ray below undefined.
    if (rx <= 0f || ry <= 0f) return distanceToSegment(x, y, from, to)

    val angle = atan2((y - cy) / ry, (x - cx) / rx)
    return hypot(x - (cx + rx * cos(angle)), y - (cy + ry * sin(angle)))
}
