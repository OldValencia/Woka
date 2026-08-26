package com.woka.feature.editor.tools.markup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import com.woka.feature.editor.core.EditorToolScope
import kotlin.math.abs

/**
 * What a markup gesture does to the document, and the two coordinate conversions it
 * needs (ME-114).
 *
 * Split from `MarkupOverlay` for the size mandate. The seam is the one that matters
 * anyway: above it, a pointer loop that decides *when*; here, four functions that decide
 * *what*, each of them one `preview` of a different shape list — the mechanism ME-113's
 * `Single` cardinality exists to allow.
 */

internal fun EditorToolScope.resize(gesture: MarkupGesture.Resize, at: MarkupPoint) =
    updateShape(gesture.id) { it.withHandle(gesture.handle, at.x, at.y) }

internal fun EditorToolScope.move(id: String, delta: Offset) =
    updateShape(id) { it.movedBy(delta.x, delta.y) }

/**
 * The stroke in progress, minted on the first movement — [drawn] is its id once it
 * exists, and the returned id is what the next event extends.
 *
 * Minting here rather than on the press is what keeps a stray click from leaving a
 * zero-sized shape in the document.
 */
internal fun EditorToolScope.draw(drawn: String?, from: MarkupPoint, at: MarkupPoint): String {
    if (drawn != null) {
        updateShape(drawn) { it.extendedTo(at) }
        return drawn
    }

    val fresh = newMarkupShape(
        kind = document.markupKind(),
        color = document.markupColor(),
        width = document.markupWidth(),
        at = from,
        wrap = wrapAt(sourceSize.width, from.x),
        fill = document.markupFill(),
        corner = document.markupCorner()
    ).extendedTo(at)
    preview(document.markupOpWith(document.markupShapes() + fresh))
    // Selected as it is drawn, so its handles are there the moment it is released.
    select(fresh.id)
    return fresh.id
}

/**
 * One shape replaced, the rest carried through — read from the **live** document each
 * time, so every event of a drag continues from what the previous one previewed.
 */
private fun EditorToolScope.updateShape(id: String, transform: (MarkupShape) -> MarkupShape) {
    val shapes = document.markupShapes()
    if (shapes.none { it.id == id }) return
    preview(document.markupOpWith(shapes.map { if (it.id == id) transform(it) else it }))
}

internal fun Matrix.sourcePoint(position: Offset): MarkupPoint {
    val mapped = map(position)
    return MarkupPoint(mapped.x, mapped.y)
}

/**
 * A **direction and distance**, not a position: the translation in the matrix must not
 * be applied to a delta, or every drag would be offset by wherever the picture happens
 * to sit on the canvas.
 */
internal fun Matrix.mapDelta(delta: Offset): Offset = map(delta) - map(Offset.Zero)

/**
 * [screen] pixels as source ones, at the zoom the canvas is drawn at.
 *
 * **Absolute**, because the mapping can be mirrored: with a flip in the preview the
 * difference along x is negative, and a negative tolerance is a target nothing can hit
 * (`CropOverlay`, 2026-08-22).
 */
internal fun EditorToolScope.sourcePixels(screen: Float): Float {
    val origin = canvasToSource.map(Offset.Zero)
    val along = canvasToSource.map(Offset(screen, 0f))
    return abs(along.x - origin.x).coerceAtLeast(1f)
}
