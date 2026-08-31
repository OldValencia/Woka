package com.woka.feature.editor.tools.adjust

import androidx.compose.ui.graphics.ColorMatrix

/**
 * The two adjustments ME-142 added, and the arithmetic that says why they are only two.
 *
 * A `ColorMatrix` is a 4×5 **affine** map on RGBA, and the backlog block that asked for
 * these listed three "still one matrix" candidates. One survives:
 *
 * - **Black Point** is a scale and an offset. It ships here.
 * - **Vibrance**, as Photos means it, raises saturation *more* for pixels that are
 *   already less saturated — the multiplier is then a function of the input's own
 *   saturation, which makes the map quadratic in its inputs and not affine at any
 *   weighting. What ships here is what the block's own words describe, "saturation
 *   weighted away from skin tones": a saturation whose channel weights spare the reds.
 *   It is **not** per-pixel vibrance, and the real one waits for ME-162's shader stage.
 * - **Brilliance** is region-specific by definition — brighten the dark parts, pull the
 *   highlights in, per area of the picture. Not affine, not even global; it moved to
 *   ME-162 when this was written.
 *
 * Split from `AdjustMatrix` under the size mandate: that file was at 123 lines and these
 * two carry the argument above. `adjustmentMatrix` still composes everything.
 */

/** Offsets are 0..255 here, as `AdjustMatrix` records at length. */
private const val WHITE = 255f

/**
 * How far the black point may travel, either way.
 *
 * **Not 1.0**, and the ceiling is the reason: at 1 the positive branch divides by zero,
 * and anywhere near it a photograph loses every shadow to a slider that has not reached
 * its end yet. At 0.45 the extreme is a strong, still-usable crush.
 */
private const val MAX_BLACK = 0.45f

/**
 * Where black lands, and everything stretched to follow it.
 *
 * `out = (in − b) / (1 − b)` above zero: the level `b` becomes black and the rest of the
 * range is stretched up to keep white at white. Below zero it is the same move
 * reversed — `out = in(1 − b) + b`, blacks lifted toward grey.
 *
 * **White is fixed in both directions**, which is what separates this from Brightness:
 * that one adds a constant and takes the whole range with it, this one pins one end and
 * moves the other. `AdjustMatrixTest` asserts the pin.
 */
fun blackPointMatrix(amount: Float): ColorMatrix {
    if (amount == 0f) return ColorMatrix()
    val black = amount / 100f * MAX_BLACK
    val scale = if (black > 0f) 1f / (1f - black) else 1f + black
    val offset = if (black > 0f) -WHITE * black * scale else -WHITE * black
    return channelMatrix(scale, scale, scale, offset = offset)
}

/**
 * Luminance, in the weights every saturation in this file agrees on.
 *
 * Written out rather than taken from `setToSaturation`, because the rows below are built
 * per channel and the two would otherwise disagree by a rounding at the edges.
 */
private const val LUMA_R = 0.213f
private const val LUMA_G = 0.715f
private const val LUMA_B = 0.072f

/**
 * How much each channel follows the saturation, and the whole of what "weighted" means.
 *
 * Red moves at 60 %: skin is the thing a saturation slider ruins first, and it is mostly
 * red. Green and blue move fully, so a pale sky and a dull leaf gain while a face does
 * not turn orange.
 */
private const val WEIGHT_R = 0.6f
private const val WEIGHT_G = 1f
private const val WEIGHT_B = 1f

/**
 * Saturation with the channels weighted — see this file's KDoc for what it is not.
 *
 * Each row is `out_c = L + w_c·s·(c − L)`, which is the same shape `setToSaturation`
 * builds with every `w` at 1.
 */
fun vibranceMatrix(amount: Float): ColorMatrix {
    if (amount == 0f) return ColorMatrix()
    val strength = 1f + amount / 100f
    return ColorMatrix(
        floatArrayOf(
            *saturationRow(WEIGHT_R * strength, 0),
            *saturationRow(WEIGHT_G * strength, 1),
            *saturationRow(WEIGHT_B * strength, 2),
            0f, 0f, 0f, 1f, 0f
        )
    )
}

/** One row: luminance everywhere, plus [weighted] of the channel's own contribution. */
private fun saturationRow(weighted: Float, channel: Int): FloatArray {
    val rest = 1f - weighted
    val row = floatArrayOf(rest * LUMA_R, rest * LUMA_G, rest * LUMA_B, 0f, 0f)
    row[channel] += weighted
    return row
}
