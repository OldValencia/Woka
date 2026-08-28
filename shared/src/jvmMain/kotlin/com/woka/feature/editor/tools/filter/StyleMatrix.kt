package com.woka.feature.editor.tools.filter

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.engine.image.isIdentity
import com.woka.feature.editor.engine.image.multiply
import com.woka.feature.editor.tools.adjust.exposureMatrix
import com.woka.feature.editor.tools.adjust.warmthMatrix

/**
 * A whole style folded into one `ColorMatrix`, or **null when none of its four parts
 * does anything** (ME-141).
 *
 * The twin of `adjustmentMatrix`, and split out of [FilterTool] for the same reason
 * that one is not inside `AdjustTool`: the arithmetic is a pure function of a
 * `ParamSet` and is asserted without a window, while the tool is a manifest and a
 * schema.
 *
 * Through the fold's own `multiply`, so a style composes exactly as two colour stages
 * from two different tools would — one product, built once. `exposureMatrix` and
 * `warmthMatrix` come from `tools.adjust` rather than being copied: they are pure
 * functions of one float, and a second spelling of either is a second thing to keep
 * in step.
 */
fun styleMatrix(params: ParamSet): ColorMatrix? {
    val tone = params[FilterTool.Tone]
    val contributions = listOfNotNull(
        undertoneMatrix(params[FilterTool.Undertone], params[FilterTool.Palette]),
        filterMatrix(params[FilterTool.Filter], params[FilterTool.Intensity]),
        exposureMatrix(tone.y),
        warmthMatrix(tone.x)
    )
    val product = contributions.fold<ColorMatrix, ColorMatrix?>(null) { accumulated, next ->
        multiply(accumulated, next)
    }
    return product?.takeUnless { it.isIdentity() }
}
