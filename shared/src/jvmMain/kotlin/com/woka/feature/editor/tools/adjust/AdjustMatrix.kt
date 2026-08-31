package com.woka.feature.editor.tools.adjust

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.engine.image.isIdentity
import com.woka.feature.editor.engine.image.multiply
import kotlin.math.pow

/**
 * Mid grey, in the units a `ColorMatrix` offset is expressed in.
 *
 * **Compose's offsets are 0..255**, not 0..1 — the fifth column is added after the
 * multiply, in the same scale the channels are sampled in. Writing contrast against
 * 0.5 here produces a picture that darkens as it is "increased", which is the one
 * mistake in this file that looks right in code and wrong on screen.
 */
private const val MID = 127.5f

/**
 * One `ColorMatrix` per adjustment, and their product (ME-111).
 *
 * **Pure, and Compose-free apart from the matrix type itself.** Every one of these is a
 * function of a single number, which is what lets the fold run them at screen scale for
 * the preview and at native scale on save from the same declaration — and what lets them
 * be asserted without a window.
 *
 * Each returns the identity at zero, so a panel that has been opened and not touched
 * composes nothing at all: `adjustmentMatrix` answers null, `stage()` skips, and the
 * fold's `colorFilter` stays null rather than becoming a filter the GPU runs over every
 * pixel of every frame.
 */

/** Rows of a 4×5 matrix: three scales down the diagonal and one shared offset. */
internal fun channelMatrix(
    red: Float,
    green: Float,
    blue: Float,
    offset: Float = 0f
): ColorMatrix = ColorMatrix(
    floatArrayOf(
        red, 0f, 0f, 0f, offset,
        0f, green, 0f, 0f, offset,
        0f, 0f, blue, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )
)

/**
 * Stops of light: a **multiply**, because that is what exposure is.
 *
 * ±1 stop across the slider — doubling and halving the light — which is the range a
 * correction wants; anything wider is a look, and looks are ME-112's.
 */
fun exposureMatrix(amount: Float): ColorMatrix {
    if (amount == 0f) return ColorMatrix()
    val factor = 2f.pow(amount / 100f)
    return channelMatrix(factor, factor, factor)
}

/**
 * Brightness: an **add**, not a multiply, which is what separates it from exposure —
 * it lifts the black point instead of scaling the whole range.
 */
fun brightnessMatrix(amount: Float): ColorMatrix {
    if (amount == 0f) return ColorMatrix()
    return channelMatrix(1f, 1f, 1f, offset = amount / 100f * MID)
}

/**
 * Contrast pivots on mid grey: `out = scale * (in - MID) + MID`, folded into the
 * matrix's own offset. A bare multiply would darken everything as contrast rose.
 */
fun contrastMatrix(amount: Float): ColorMatrix {
    if (amount == 0f) return ColorMatrix()
    val scale = 1f + amount / 100f
    return channelMatrix(scale, scale, scale, offset = (1f - scale) * MID)
}

/**
 * Saturation, through Compose's own `setToSaturation` — the luminance weights are
 * standard and there is no reason for this file to hold a second copy of them.
 *
 * −100 lands on 0, which is grey; +100 lands on 2.
 */
fun saturationMatrix(amount: Float): ColorMatrix {
    if (amount == 0f) return ColorMatrix()
    return ColorMatrix().apply { setToSaturation(1f + amount / 100f) }
}

/** Warmth runs red against blue: sunlight one way, shade the other. */
fun warmthMatrix(amount: Float): ColorMatrix {
    if (amount == 0f) return ColorMatrix()
    val shift = amount / 100f * 0.3f
    return channelMatrix(1f + shift, 1f, 1f - shift)
}

/** Tint runs green against magenta, which is the other axis a white balance has. */
fun tintMatrix(amount: Float): ColorMatrix {
    if (amount == 0f) return ColorMatrix()
    val shift = amount / 100f * 0.3f
    return channelMatrix(1f - shift / 2f, 1f + shift, 1f - shift / 2f)
}

/**
 * The eight folded into one, or **null when none of them does anything**.
 *
 * Multiplied through the fold's own `multiply`, so this composes exactly as two colour
 * stages from two different tools would — one product, built once.
 *
 * **The order is not free.** Black Point is a level operation and sits with the other
 * two, before anything touches colour: a saturation applied first would then be
 * re-scaled by the stretch, and the slider would mean something different depending on
 * where the black point happened to be. `AdjustOrderTest` asserts it on the product,
 * because the call order below is what a later edit changes silently (ME-142).
 */
fun adjustmentMatrix(params: ParamSet): ColorMatrix? {
    val matrices = listOf(
        exposureMatrix(params[AdjustTool.Exposure]),
        brightnessMatrix(params[AdjustTool.Brightness]),
        contrastMatrix(params[AdjustTool.Contrast]),
        blackPointMatrix(params[AdjustTool.BlackPoint]),
        saturationMatrix(params[AdjustTool.Saturation]),
        vibranceMatrix(params[AdjustTool.Vibrance]),
        warmthMatrix(params[AdjustTool.Warmth]),
        tintMatrix(params[AdjustTool.Tint])
    )
    val product = matrices.fold<ColorMatrix, ColorMatrix?>(null) { accumulated, next ->
        multiply(accumulated, next)
    }
    return product?.takeUnless { it.isIdentity() }
}
