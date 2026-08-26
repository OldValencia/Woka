package com.woka.feature.editor.tools.markup

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * How a caption is measured (ME-122, ME-146) — split from `MarkupText` at the size mandate,
 * along the line that already divided it: the font and the layout here, what is drawn from
 * that layout there.
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
 * a HiDPI display as in the file that display saved. It is what lets `wrap` be passed
 * straight into [Constraints] as well, since a layout pixel is a source pixel here.
 */
private val markupTextMeasurer: TextMeasurer by lazy {
    TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        defaultDensity = Density(1f),
        defaultLayoutDirection = LayoutDirection.Ltr
    )
}

/** The floor under an inherited wrap width, as a fraction of the picture: one quarter. */
private const val MIN_WRAP = 4

/**
 * The width a caption placed at [x] on a picture [sourceWidth] wide wraps against
 * (ME-146).
 *
 * **The picture's own width, measured from where the caption starts**, so the words break
 * at the right edge rather than a quarter-picture past it. Floored at a quarter of the
 * width because a caret placed at 95 % would otherwise inherit a one-character column.
 *
 * Spelled once and read by both places a caption is minted — `textEditAt` for the caret
 * and `newMarkupShape` for the factory — because two expressions of one rule is how the
 * second one drifts.
 */
internal fun wrapAt(sourceWidth: Int, x: Float): Float =
    (sourceWidth - x).coerceAtLeast(sourceWidth.toFloat() / MIN_WRAP)

/**
 * The layout of [shape]'s string at its own size, or **null for a shape with no text**.
 *
 * The null is the rule the rest of the package reads: nothing draws, nothing is hit and
 * nothing is measured for a shape nobody has typed into, so an empty one cannot become
 * an invisible mark in the document (ME-145 is where one can exist at all).
 *
 * A `wrap` of zero measures unconstrained on one line, which is what every document
 * written before ME-146 says and therefore what it must go on meaning.
 */
internal fun textLayoutOf(shape: MarkupShape.Text): TextLayoutResult? {
    if (shape.text.isBlank()) return null

    val constraints = if (shape.wrap > 0f) {
        Constraints(maxWidth = shape.wrap.roundToInt().coerceAtLeast(1))
    } else {
        Constraints()
    }
    return markupTextMeasurer.measure(
        text = shape.text,
        style = TextStyle(fontSize = shape.width.sp),
        constraints = constraints
    )
}
