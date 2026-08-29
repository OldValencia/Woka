package com.woka.feature.editor.tools.markup

import androidx.compose.ui.geometry.Offset
import com.woka.feature.editor.tools.markup.MarkupShape.Arrow
import com.woka.feature.editor.tools.markup.MarkupShape.Ellipse
import com.woka.feature.editor.tools.markup.MarkupShape.Freehand
import com.woka.feature.editor.tools.markup.MarkupShape.Rectangle
import com.woka.feature.editor.tools.markup.MarkupShape.Text
import kotlin.math.hypot

/**
 * Hit testing, handles and the arithmetic of moving a shape — pure, in source pixels
 * (ME-113). The distances themselves are `MarkupDistance`.
 *
 * **All of it is model arithmetic on purpose**, so ME-114 is a gesture recogniser and
 * nothing else: an overlay converts a pointer to source coordinates and calls one of
 * these four. The alternative — hit tests written inside the gesture handler — is how
 * one distance rule ends up spelled twice and the second spelling drifts.
 *
 * [tolerance] is the caller's, in source pixels, for the reason `CropRect.handleAt`
 * gives: it is a *screen* distance converted back through the canvas scale, and only the
 * overlay knows what that scale currently is. It is measured to the shape's **outline**
 * and the stroke's own width is not added here — a caller wanting a thick shape to be
 * easier to grab passes `grab + width / 2` and says so.
 */
fun MarkupShape.hitTest(x: Float, y: Float, tolerance: Float): Boolean = when (this) {
    is Arrow -> distanceToSegment(x, y, from, to) <= tolerance
    // The outline, not the area: these are stroked and hollow, so the inside of a box
    // drawn around a face belongs to whatever is under it.
    is Rectangle -> distanceToOutline(x, y, corners(from, to)) <= tolerance
    is Ellipse -> distanceToEllipse(x, y, from, to) <= tolerance
    is Freehand -> points.any { hypot(x - it.x, y - it.y) <= tolerance } ||
        points.zipWithNext().any { (a, b) -> distanceToSegment(x, y, a, b) <= tolerance }
    // **Filled, not outlined** — the one shape tested that way. Type is a solid mark and
    // the space between its letters belongs to it, unlike the inside of a drawn box. The
    // empty check is not defensive: `Rect.Zero` inflated by the tolerance is a target a
    // press in the picture's top-left corner would otherwise hit.
    is Text -> textBounds(this).let { !it.isEmpty && it.inflate(tolerance).contains(Offset(x, y)) }
}

/**
 * The points a drag can grab to reshape this — two for an arrow, four for a box, an
 * ellipse or a caption, **none** for a freehand stroke, which has no box to pull on.
 *
 * The order is the order [withHandle] indexes, and for the two stored as a pair these are
 * the stored corners rather than normalised ones: index 0 is always the `from` corner,
 * whichever side of `to` it has ended up on, so dragging a corner past its opposite goes
 * on working instead of changing which handle is under the pointer. A caption has no such
 * pair — its box is measured — so its four are walked from its top-left.
 */
fun MarkupShape.handles(): List<MarkupPoint> = when (this) {
    is Arrow -> listOf(from, to)
    is Rectangle -> corners(from, to)
    is Ellipse -> corners(from, to)
    is Freehand -> emptyList()
    // The four corners of the caption's box, plate included — and dragging one **scales
    // the type** rather than reshaping a rectangle (ME-148, `MarkupTextFrame`).
    is Text -> frameHandles()
}

/** Every point translated, and nothing else — the whole of dragging a shape. */
fun MarkupShape.movedBy(dx: Float, dy: Float): MarkupShape = when (this) {
    is Arrow -> copy(from = from.shifted(dx, dy), to = to.shifted(dx, dy))
    is Rectangle -> copy(from = from.shifted(dx, dy), to = to.shifted(dx, dy))
    is Ellipse -> copy(from = from.shifted(dx, dy), to = to.shifted(dx, dy))
    is Freehand -> copy(points = points.map { it.shifted(dx, dy) })
    is Text -> copy(at = at.shifted(dx, dy))
}

/**
 * The shape with handle [index] moved to [x], [y] — the opposite corner, or the other
 * end of an arrow, left exactly where it was.
 *
 * An index this shape has no handle for returns the shape unchanged rather than
 * throwing: the caller is a pointer event, and a gesture arriving a frame after the
 * selected shape was replaced must not take the editor down with it.
 */
fun MarkupShape.withHandle(index: Int, x: Float, y: Float): MarkupShape = when (this) {
    is Arrow -> when (index) {
        0 -> copy(from = MarkupPoint(x, y))
        1 -> copy(to = MarkupPoint(x, y))
        else -> this
    }
    is Rectangle -> boxWithCorner(from, to, index, x, y)
        ?.let { (a, b) -> copy(from = a, to = b) }
        ?: this
    is Ellipse -> boxWithCorner(from, to, index, x, y)
        ?.let { (a, b) -> copy(from = a, to = b) }
        ?: this
    is Freehand -> this
    // A ratio, not a corner: the type, the box and the line breaks move together
    // (ME-148). The opposite corner is what stays still, as it does for a box above.
    is Text -> withFrameHandle(index, x, y)
}

private fun MarkupPoint.shifted(dx: Float, dy: Float) = MarkupPoint(x + dx, y + dy)

/** The four corners of the box [from]–[to], walked round from `from`. */
internal fun corners(from: MarkupPoint, to: MarkupPoint): List<MarkupPoint> = listOf(
    from,
    MarkupPoint(to.x, from.y),
    to,
    MarkupPoint(from.x, to.y)
)

/**
 * The box with one corner moved, as a new `from`/`to` pair — shared by the rectangle and
 * the ellipse, which differ only in what is drawn inside the same two points.
 */
private fun boxWithCorner(
    from: MarkupPoint,
    to: MarkupPoint,
    index: Int,
    x: Float,
    y: Float
): Pair<MarkupPoint, MarkupPoint>? = when (index) {
    0 -> MarkupPoint(x, y) to to
    1 -> MarkupPoint(from.x, y) to MarkupPoint(x, to.y)
    2 -> from to MarkupPoint(x, y)
    3 -> MarkupPoint(x, from.y) to MarkupPoint(to.x, y)
    else -> null
}
