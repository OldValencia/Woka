package com.woka.feature.editor.tools.markup

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import kotlin.math.min

/**
 * Where a caption lands and how it is drawn (ME-122, ME-146). What it *measures* is
 * `MarkupTextLayout`, split off at the size mandate.
 */

/** The plate's padding, as a fraction of the type's own size. */
private const val PLATE_PAD = 0.2f

/**
 * Where [shape] sits, in source pixels — `at` is its **top-left**, which is what both
 * the layout and Skia measure from.
 *
 * **The plate is part of it when there is one**, so a caption on a background is grabbed
 * by the background the eye sees rather than by the glyphs inside it; a caption without
 * one keeps exactly the box ME-122 gave it.
 *
 * `Rect.Zero` for an empty string, and callers check [Rect.isEmpty] rather than trusting
 * the origin: an empty rect at (0, 0) inflated by a grab tolerance is a target a press
 * in the corner of the picture would otherwise hit.
 */
internal fun textBounds(shape: MarkupShape.Text): Rect {
    val layout = textLayoutOf(shape) ?: return Rect.Zero

    // **The longest line, never `layout.size.width`** — measured 2026-08-27, that one
    // reports the *constraint*: a block wrapped at 300 px whose longest line is 266 px
    // still measures 300, so the plate ran 34 px past the type and every one of them
    // landed on the right. A caption looked pushed into the left of its own background.
    val longest = (0 until layout.lineCount).maxOf { line -> layout.getLineRight(line) }
    val box = Rect(
        offset = Offset(shape.at.x, shape.at.y),
        size = Size(longest, layout.size.height.toFloat())
    )
    return if (shape.hasPlate()) box.inflate(shape.width * PLATE_PAD) else box
}

/**
 * The plate, then the string, drawn at whatever scale the caller has set — the same
 * contract every other branch of `drawMarkup` follows.
 *
 * **Measured first, then drawn from the result.** The `drawText(measurer, …)` overload
 * re-measures against the draw scope's density, which is the one thing this file exists
 * to avoid; the `TextLayoutResult` overload draws what was already laid out and honours
 * the canvas transform, so a preview at fit scale and a save at 1.0 differ only in that
 * transform.
 *
 * The plate is one `drawRoundRect` **before** the glyphs, inside the same
 * `RasterStage.Paint` the shapes already share — no new stage kind, so §3.2 stands.
 */
internal fun DrawScope.drawMarkupText(shape: MarkupShape.Text, color: Color) {
    val layout = textLayoutOf(shape) ?: return

    if (shape.hasPlate()) {
        val plate = textBounds(shape)
        drawRoundRect(
            color = Color(shape.fill),
            topLeft = plate.topLeft,
            size = plate.size,
            cornerRadius = CornerRadius(plateRadius(shape, plate))
        )
    }
    drawText(
        textLayoutResult = layout,
        color = color,
        topLeft = Offset(shape.at.x, shape.at.y)
    )
}

/**
 * The corner, in source pixels: [MarkupShape.Text.corner] per cent **of the type's own
 * size**, so a caption scaled by a corner drag (ME-148) keeps the roundness it was given
 * without the ratio having to touch a second field.
 *
 * Clamped to half the shorter side, which is what makes 100 % a capsule rather than a
 * shape Skia has to guess at: a radius larger than the box it rounds is undefined, and a
 * one-word caption's plate is much shorter than it is wide.
 */
private fun plateRadius(shape: MarkupShape.Text, plate: Rect): Float =
    min(shape.width * shape.corner / 100f, min(plate.width, plate.height) / 2f)

/** A plate with no alpha is no plate at all — the `0L` default read as what it means. */
private fun MarkupShape.Text.hasPlate(): Boolean = (fill ushr 24) != 0L
