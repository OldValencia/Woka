package com.woka.feature.editor.tools.markup

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/**
 * Everything about a text shape that needs a font: how large it measures, where it
 * lands, and how it is drawn (ME-122).
 *
 * **The measurer cannot come from a composition.** `drawMarkup` is a plain `DrawScope`
 * extension and three different callers reach it — the editing canvas, a preview-choice
 * thumbnail and `renderAtNativeScale` on the save thread — and only one of them is
 * composing. `rememberTextMeasurer` is unavailable to the other two, so the measurer is
 * built here, once, and shared by all three. Its own cache is what keeps a stroke drawn
 * over a caption from re-laying that caption out sixty times a second.
 *
 * **[Density]`(1f)` is the whole trick.** It makes one `sp` one pixel, so
 * `fontSize = shape.width.sp` is exactly `shape.width` *source* pixels — the unit every
 * other markup value is already in. Measuring against the draw scope's own density
 * instead would size type by the screen it is drawn on: twice as large on the canvas of
 * a HiDPI display as in the file that display saved.
 */
private val markupTextMeasurer: TextMeasurer by lazy {
    TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr
    )
}

/**
 * The layout of [shape]'s string at its own size, or **null for a shape with no text**.
 *
 * The null is the rule the rest of the package reads: nothing draws, nothing is hit and
 * nothing is measured for a shape nobody has typed into, so an empty one cannot become
 * an invisible mark in the document (ME-145 is where one can exist at all).
 */
internal fun textLayoutOf(shape: MarkupShape.Text): TextLayoutResult? {
    if (shape.text.isBlank()) return null

    return markupTextMeasurer.measure(
        text = shape.text,
        style = TextStyle(fontSize = shape.width.sp)
    )
}

/**
 * Where [shape] sits, in source pixels — `at` is its **top-left**, which is what both
 * the layout and Skia measure from.
 *
 * `Rect.Zero` for an empty string, and callers check [Rect.isEmpty] rather than trusting
 * the origin: an empty rect at (0, 0) inflated by a grab tolerance is a target a press
 * in the corner of the picture would otherwise hit.
 */
internal fun textBounds(shape: MarkupShape.Text): Rect {
    val layout = textLayoutOf(shape) ?: return Rect.Zero

    return Rect(
        offset = Offset(shape.at.x, shape.at.y),
        size = Size(layout.size.width.toFloat(), layout.size.height.toFloat())
    )
}

/**
 * The string, drawn at whatever scale the caller has set — the same contract every other
 * branch of `drawMarkup` follows.
 *
 * **Measured first, then drawn from the result.** The `drawText(measurer, …)` overload
 * re-measures against the draw scope's density, which is the one thing this file exists
 * to avoid; the `TextLayoutResult` overload draws what was already laid out and honours
 * the canvas transform, so a preview at fit scale and a save at 1.0 differ only in that
 * transform.
 */
internal fun DrawScope.drawMarkupText(shape: MarkupShape.Text, color: Color) {
    val layout = textLayoutOf(shape) ?: return

    drawText(
        textLayoutResult = layout,
        color = color,
        topLeft = Offset(shape.at.x, shape.at.y)
    )
}
