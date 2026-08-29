package com.woka.feature.editor.tools.filter

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.engine.image.isIdentity
import com.woka.feature.editor.engine.image.multiply

/**
 * A whole style folded into one `ColorMatrix`, or **null when neither of its two parts
 * does anything** (ME-141, ME-150).
 *
 * The twin of `adjustmentMatrix`, and split out of [FilterTool] for the same reason
 * that one is not inside `AdjustTool`: the arithmetic is a pure function of a
 * `ParamSet` and is asserted without a window, while the tool is a manifest and a
 * schema.
 *
 * Through the fold's own `multiply`, so a style composes exactly as two colour stages
 * from two different tools would — one product, built once. The pad is not a third
 * contribution: it shapes the look from inside `filterMatrix` and reaches no pixel of
 * its own, which is what keeps Styles from being a second Exposure and a second Warmth
 * over the ones `tools.adjust` already ships.
 */
fun styleMatrix(params: ParamSet): ColorMatrix? {
    val contributions = listOfNotNull(
        undertoneMatrix(params[FilterTool.Undertone], params[FilterTool.Palette]),
        filterMatrix(
            params[FilterTool.Filter],
            params[FilterTool.Intensity],
            params[FilterTool.Tone]
        )
    )
    val product = contributions.fold<ColorMatrix, ColorMatrix?>(null) { accumulated, next ->
        multiply(accumulated, next)
    }
    return product?.takeUnless { it.isIdentity() }
}
