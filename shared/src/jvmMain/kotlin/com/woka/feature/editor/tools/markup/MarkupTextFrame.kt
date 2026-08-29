package com.woka.feature.editor.tools.markup

import androidx.compose.ui.geometry.Offset

/**
 * The caption's frame: where its corners are, and what dragging one does (ME-148).
 *
 * Its own file because `MarkupGeometry` is at the size mandate, and because this is one
 * idea rather than four branches of a `when` — **a corner scales the type** (settled
 * 2026-08-27, the iOS Photos reading). Not the text-editor reading, where a corner sets
 * the wrap width and leaves the size alone.
 *
 * So a drag is a **ratio**, never a rectangle. It multiplies `width` — the font size since
 * ME-122 — and `wrap` — the box since ME-146 — by the same factor, which is what keeps the
 * block's proportions and lands the line breaks where they were. `corner` takes part in
 * nothing: ME-146 made it a per cent of the type's size precisely so it would scale free.
 */

/**
 * The four corners of [textBounds], walked `topLeft, topRight, bottomRight, bottomLeft`,
 * so handle `i`'s anchor is always `(i + 2) % 4`.
 *
 * The **bounds**, not the glyph box: with a plate the two differ by its padding, and the
 * frame has to sit on what the eye sees. Empty for a caption nobody has typed into —
 * `textBounds` is `Rect.Zero` there, and four handles in the picture's corner are a target
 * nobody meant to hit, which is the guard `hitTest` already makes.
 */
internal fun MarkupShape.Text.frameHandles(): List<MarkupPoint> {
    val box = textBounds(this)
    if (box.isEmpty) return emptyList()

    return listOf(
        MarkupPoint(box.left, box.top),
        MarkupPoint(box.right, box.top),
        MarkupPoint(box.right, box.bottom),
        MarkupPoint(box.left, box.bottom)
    )
}

/**
 * The caption scaled by [ratio] about [anchor] — the one operation both a corner drag and
 * the `Text size` row go through.
 *
 * **The clamp is read back.** `width` is bounded by the row that also writes it
 * (`MarkupTextSizeParam`), and without taking the ratio actually applied from that result,
 * a caption driven past 512 px would keep widening its box while its glyphs stopped
 * growing — the proportions this whole function exists to preserve, lost at the one place
 * a user would push hardest.
 *
 * [anchor] stays exactly put by construction: `textBounds` is `at` plus a measured size
 * plus a plate pad, and all three scale by `r`, so the far corner maps onto itself.
 */
internal fun MarkupShape.Text.scaled(ratio: Float, anchor: Offset): MarkupShape.Text {
    val sized = (width * ratio).coerceIn(MarkupTextSizeParam.range)
    val applied = sized / width

    return copy(
        width = sized,
        wrap = wrap * applied,
        at = MarkupPoint(
            x = anchor.x + (at.x - anchor.x) * applied,
            y = anchor.y + (at.y - anchor.y) * applied
        )
    )
}

/**
 * Handle [index] dragged to [x], [y] — the opposite corner left where it was.
 *
 * The ratio is the pointer **projected onto the box's diagonal**, rather than
 * `max(|dx| / |dx₀|, |dy| / |dy₀|)`: the box is not square, so the two axes report
 * different numbers for one drag, and choosing either makes the corner slide off the
 * pointer along the other. The projection is the number that puts the corner as near the
 * pointer as a shape keeping its proportions can be.
 *
 * An index this caption has no handle for — or a degenerate box — returns it unchanged,
 * the rule `withHandle` already keeps for every other kind: the caller is a pointer event,
 * and one arriving a frame late must not take the editor down.
 */
internal fun MarkupShape.Text.withFrameHandle(index: Int, x: Float, y: Float): MarkupShape {
    val corners = frameHandles()
    val corner = corners.getOrNull(index) ?: return this
    val anchor = corners[(index + 2) % corners.size]

    val dx = corner.x - anchor.x
    val dy = corner.y - anchor.y
    val diagonal = dx * dx + dy * dy
    if (diagonal <= 0f) return this

    val ratio = ((x - anchor.x) * dx + (y - anchor.y) * dy) / diagonal
    // Never zero or behind the anchor: dragging a corner through its opposite would
    // otherwise invert the caption, and type has no mirrored form to invert into.
    return scaled(ratio.coerceAtLeast(MIN_RATIO), Offset(anchor.x, anchor.y))
}

/** The smallest drag that still leaves a caption to grab; the size clamp does the rest. */
private const val MIN_RATIO = 0.01f
