package com.woka.feature.editor.tools.markup

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.FloatParam
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What a markup shape *starts* at, derived from the picture rather than fixed (ME-159).
 *
 * **The complaint this answers is about the first action, not about legibility.** Both
 * sizes used to be constants in source pixels, so a caption arrived at 64 px on a
 * 300 × 400 picture and at 64 px on a 30 000 × 40 000 one. The number never moved because
 * nothing here had ever looked at what was opened, and on every file but the one size the
 * constant was tuned for, placing a caption was two actions: place it, then go and fix it.
 * There is no better constant to choose — the two cases above are two orders of magnitude
 * apart.
 *
 * **Relative to the shorter edge of the source.** Not to `EditorEngine.drawnSize`, which
 * moves when a crop lands and would make a caption resize itself when the crop is applied;
 * and not to the canvas, which would bake different pixels depending on the window and is
 * not even present when `RecipeStillEdits` folds. The shorter edge is a property of the
 * file, which is what makes it answer the complaint.
 *
 * **Only the default is derived. The range is not** — it is a wide constant, 1 px to
 * 1000 px, chosen by hand 2026-08-31. A range that scaled with the picture would have made
 * the slider's track mean something different per file, and 1000 px covers the cases that
 * exist. The consequence is written down rather than hidden: past a shorter edge of about
 * 16 700 px the derived default would exceed the ceiling and is clamped to it, so a caption
 * on a 30 000 px picture starts at 1000 px instead of 1800 — still fifteen times what it
 * used to be.
 *
 * **Nothing stored changes.** `MarkupShape.width` stays an absolute number of source
 * pixels, so every recipe written before this renders exactly as it did.
 */

/** Type, as a fraction of the shorter edge — 64 px on a 1000 px picture, as before. */
private const val TEXT_FRACTION = 0.06f

/** A stroke, the same way — 6 px on a 1000 px picture. */
private const val STROKE_FRACTION = 0.006f

/**
 * What a size row offers, for both of them. Wide on purpose: the old 12–512 for type and
 * 1–64 for a stroke could not reach either end of the range of pictures Woka opens, and
 * the corner drag clamps into this same span (`MarkupTextFrame.scaled`).
 */
internal val MARKUP_SIZE_RANGE = 1f..1000f

/**
 * [fraction] of the shorter edge, inside [MARKUP_SIZE_RANGE]. Zero-sized sources fall back.
 *
 * **Rounded to whole pixels**, because this number is read as well as used: the row prints
 * it, and `2.88px` under a control is a worse start than `3px`. Both old constants were
 * whole numbers and every derived one now is too.
 */
private fun derivedSize(sourceSize: IntSize, fraction: Float, fallback: Float): Float {
    val shorterEdge = min(sourceSize.width, sourceSize.height)
    if (shorterEdge <= 0) return fallback
    return (shorterEdge * fraction).roundToInt().toFloat().coerceIn(MARKUP_SIZE_RANGE)
}

/**
 * What one press of the row's `−` or `+` is worth here.
 *
 * **Read off the default rather than off the range**, which is the whole reason
 * `FloatParam.step` exists. The default rule takes the span, and one wide constant span
 * now serves a 1.8 px stroke on a small picture and a 180 px one on a large: a step of
 * five cannot nudge the first at all, and a step of one is sixty presses on the second.
 * A twelfth of the default is a press worth taking on both, and it reproduces the old
 * numbers exactly where the old constants sat — 6 px stroke steps by 1, 64 px type by 5.
 */
private fun stepFor(default: Float): Float = (default / 12f).roundToInt().coerceAtLeast(1).toFloat()

/**
 * The two specs a panel and a fresh shape both read, with their defaults derived for this
 * picture. Same keys, same range, same `encode` — so a write is byte-for-byte what it was
 * and a document written by either version means the same thing.
 */
internal fun textSizeSpecFor(sourceSize: IntSize): FloatParam {
    val default = derivedSize(sourceSize, TEXT_FRACTION, MarkupTextSizeParam.default)
    return MarkupTextSizeParam.copy(default = default, detent = default, step = stepFor(default))
}

internal fun widthSpecFor(sourceSize: IntSize): FloatParam {
    val default = derivedSize(sourceSize, STROKE_FRACTION, MarkupWidthParam.default)
    return MarkupWidthParam.copy(default = default, detent = default, step = stepFor(default))
}
