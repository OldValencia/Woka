package com.woka.feature.editor.tools.markup

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit

/**
 * What a caption *looks* like and how tall it really is — split from `MarkupTextLayout`
 * at the size mandate when ME-152 added the band, along the seam that was already there:
 * measuring the string on that side, the style it is measured in and the box it occupies
 * on this one.
 */

/**
 * How [shape] is styled, at [fontSize] and in [color] (ME-151).
 *
 * **One function, two callers, and that is the point.** The fold measures a caption at its
 * source size to draw it; `MarkupTextField` measures the *same* caption at the canvas
 * scale to put a caret on it. A bold advance is wider than a regular one, so two spellings
 * of "what this caption looks like" put the caret somewhere the glyphs are not — the same
 * class of defect ME-146 fixed when a wrapped line left a single-line field behind.
 *
 * [fontSize] is the caller's precisely because those two numbers differ; everything else
 * comes off the shape.
 */
internal fun markupTextStyle(
    shape: MarkupShape.Text,
    fontSize: TextUnit,
    color: Color = Color.Unspecified
): TextStyle = TextStyle(
    color = color,
    fontSize = fontSize,
    // Null, not `FontWeight.Normal`: the style is measured against the resolver's own
    // default face, and naming a weight would pick one for a font that may not have it.
    fontWeight = if (TextFace.Bold in shape.faces) FontWeight.Bold else null,
    fontStyle = if (TextFace.Italic in shape.faces) FontStyle.Italic else null,
    textDecoration = decorationOf(shape.faces)
)

/**
 * The two line decorations as one value — `combine`, because both may be on at once and
 * `TextStyle` has one slot for them. Null for neither, which leaves the slot untouched.
 */
private fun decorationOf(faces: Set<TextFace>): TextDecoration? {
    val lines = buildList {
        if (TextFace.Underline in faces) add(TextDecoration.Underline)
        if (TextFace.Strikethrough in faces) add(TextDecoration.LineThrough)
    }
    return if (lines.isEmpty()) null else TextDecoration.combine(lines)
}

/**
 * How far the type's ink reaches above and below the baseline, as a fraction of the font
 * size — **measured, not assumed** (ME-152, 2026-08-29).
 *
 * Rasterised at 48, 64 and 128 px over Latin and Cyrillic: ascenders reach `0.749 em`
 * (Cyrillic capitals `0.70`) and descenders `0.235 em`, both stable across every size
 * tried. Rounded to `0.75` and `0.25`, which is `1.0 em` of box and leaves the descender
 * a hair of clearance.
 *
 * Against the **`1.33 em` line box** the layout reports, which is what the plate, the
 * frame, the handles and the caret were all built from before this. That box carries the
 * font's internal leading above the ascender and reserves the descent whether or not
 * anything descends into it — 85 px of box for 48 px of glyphs at size 64.
 *
 * **Constants rather than the string's own ink**, deliberately: a plate measured from the
 * letters actually typed would change height as the caption is edited, jumping the moment
 * a `g` is typed.
 */
private const val CAPTION_ASCENT = 0.75f

private const val CAPTION_DESCENT = 0.25f

/**
 * The caption's **visual** vertical extent, relative to its `at.y` and in source pixels —
 * the one band the plate, the selection frame, the handles and the caret all use (ME-152).
 *
 * Built from the baselines rather than from the layout's height, because `lineHeight` and
 * `LineHeightStyle.trim` were measured to have **no effect on desktop** the same day: all
 * of `1.0 em`, `0.96 em` and `trim = Both` returned the identical 85 px. So the trimming
 * is arithmetic here instead of a property of the style.
 *
 * First baseline for the top and last for the bottom, so a wrapped caption's band covers
 * every line it has.
 */
internal fun captionBand(shape: MarkupShape.Text): ClosedFloatingPointRange<Float>? {
    val layout = textLayoutOf(shape) ?: return null
    return (layout.firstBaseline - shape.width * CAPTION_ASCENT)..
        (layout.lastBaseline + shape.width * CAPTION_DESCENT)
}
