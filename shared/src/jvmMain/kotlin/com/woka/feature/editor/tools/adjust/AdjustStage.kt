package com.woka.feature.editor.tools.adjust

import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolStage
import com.woka.feature.editor.engine.image.RasterStage

/**
 * The twelve sliders collapsed into what the fold draws with, or **nothing at all when
 * every one of them is neutral** (ME-162).
 *
 * Its own file because `AdjustTool` is at the size mandate, and the seam is a real one:
 * that file declares *what the tool is* — twelve specs and a manifest — and this one
 * decides *what it performs*. Both are pure; neither knows about Compose.
 *
 * **The order is the picture the user is dragging towards.** Colour first, then the tone
 * curves, then noise, then sharpen. Noise before sharpen because sharpening noise
 * amplifies it into the detail the sharpening was meant to reveal — and because grain
 * added first is grain the sharpener then bites on, which is what film grain looks like;
 * sharpen last because it is what the eye checks, and it should check finished colour.
 *
 * One stage is returned bare rather than wrapped: a document with only Exposure moved
 * produces exactly the `Pixels` stage it always did, so nothing that shipped before this
 * ticket takes a different path through the fold.
 */
fun adjustStage(params: ParamSet): ToolStage? {
    val stages = listOfNotNull(
        adjustmentMatrix(params)?.let { RasterStage.Pixels(it) },
        toneShaderStage(params[AdjustTool.Highlights], params[AdjustTool.Shadows]),
        noiseShaderStage(params[AdjustTool.Noise]),
        sharpenShaderStage(params[AdjustTool.Sharpness])
    )
    return when {
        stages.isEmpty() -> null
        stages.size == 1 -> stages.single()
        else -> RasterStage.Composite(stages)
    }
}
