package com.woka.feature.editor.tools.markup

import com.woka.feature.editor.tools.markup.MarkupShape.Arrow
import com.woka.feature.editor.tools.markup.MarkupShape.Ellipse
import com.woka.feature.editor.tools.markup.MarkupShape.Freehand
import com.woka.feature.editor.tools.markup.MarkupShape.Rectangle
import kotlin.math.hypot

/**
 * What a press on the markup overlay means, and what a drag then does to a shape
 * (ME-114) — pure, so the overlay is a gesture recogniser and nothing else.
 *
 * Split from `MarkupOverlay` for the size mandate, along the seam that decides how this
 * reads: what a press *is* can be tested without a pointer, and is.
 */
internal sealed interface MarkupGesture {

    /** A handle of the selected shape, by its index in `handles()`. */
    data class Resize(val id: String, val handle: Int) : MarkupGesture

    data class Move(val id: String) : MarkupGesture

    /** Empty picture: a new shape, or — with something selected — a deselect. */
    data object Draw : MarkupGesture
}

/**
 * What the press at [x], [y] grabbed, in source pixels.
 *
 * **The selected shape's handles win over every shape's body**, because a handle sits on
 * the outline it belongs to: without this rule, grabbing a corner of the box you just
 * drew would move the whole box instead of resizing it.
 *
 * Bodies are tested **topmost first** — the last entry is drawn last and is the one the
 * user sees on top, so it must be the one they grab.
 */
internal fun markupGestureAt(
    shapes: List<MarkupShape>,
    selected: MarkupShape?,
    x: Float,
    y: Float,
    grab: Float
): MarkupGesture {
    val handle = selected?.handleAt(x, y, grab)
    if (selected != null && handle != null) return MarkupGesture.Resize(selected.id, handle)

    // The stroke's own width, so a thick mark is as easy to grab as it looks — the
    // addition `MarkupGeometry.hitTest` documents and deliberately does not make itself.
    val hit = shapes.asReversed().firstOrNull { it.hitTest(x, y, grab + it.width / 2f) }
    return hit?.let { MarkupGesture.Move(it.id) } ?: MarkupGesture.Draw
}

/** The nearest handle within [grab], or null. */
private fun MarkupShape.handleAt(x: Float, y: Float, grab: Float): Int? = handles()
    .withIndex()
    .filter { (_, point) -> hypot(x - point.x, y - point.y) <= grab }
    .minByOrNull { (_, point) -> hypot(x - point.x, y - point.y) }
    ?.index

/**
 * A shape of [kind] that starts and ends at [at], carrying the pen it was drawn with.
 *
 * **Not created on the press** (see `MarkupOverlay`): it is minted on the first movement,
 * so a stray click on empty picture leaves nothing behind.
 */
internal fun newMarkupShape(
    kind: MarkupKind,
    color: Long,
    width: Float,
    at: MarkupPoint
): MarkupShape {
    val id = newShapeId()
    return when (kind) {
        MarkupKind.Arrow -> Arrow(id, color, width, at, at)
        MarkupKind.Rectangle -> Rectangle(id, color, width, at, at)
        MarkupKind.Ellipse -> Ellipse(id, color, width, at, at)
        MarkupKind.Freehand -> Freehand(id, color, width, listOf(at))
    }
}

/**
 * The shape as the drag has it now: the far end follows the pointer, and a freehand
 * stroke **accumulates** instead.
 *
 * A point nearer than one source pixel to the last one is dropped. A 6000 px photograph
 * at 60 fps otherwise stores a thousand points for one stroke, and every one of them is
 * written into the user's file (ME-114 step 4).
 */
internal fun MarkupShape.extendedTo(point: MarkupPoint): MarkupShape = when (this) {
    is Arrow -> copy(to = point)
    is Rectangle -> copy(to = point)
    is Ellipse -> copy(to = point)
    is Freehand -> {
        val last = points.last()
        if (hypot(point.x - last.x, point.y - last.y) < 1f) this else copy(points = points + point)
    }
}
