package com.woka.feature.editor.tools.resize

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RasterStage

/**
 * What the size choices of `Save your changes?` and `Create a copy` mean to the document
 * (RD-401): the saved Resize read as a choice, a choice written back as an op, and a copy's
 * size standing in for the document's.
 */

/** A size the dialogs can hold. `Original` is no Resize at all. */
sealed interface SizeChoice {
    data object Original : SizeChoice

    /** A proportion of the frame — the 75 / 50 / 25 % presets, or a legacy slider value. */
    data class Percent(val percent: Float) : SizeChoice

    /** Explicit pixels, either axis free; [keepProportions] is only remembered for the dialog. */
    data class Custom(val width: Int, val height: Int, val keepProportions: Boolean) : SizeChoice
}

/** The Resize this document saves, as the dialog shows it. */
fun EditDocument.savedSize(): SizeChoice {
    val params = opsOf(ResizeTool.manifest.id).lastOrNull()?.params ?: return SizeChoice.Original
    val width = params[ResizeTool.Width]
    val height = params[ResizeTool.Height]
    if (width > 0 && height > 0) return SizeChoice.Custom(width, height, params[ResizeTool.Proportional])
    val percent = params[ResizeTool.Scale]
    return if (percent >= FULL_PERCENT) SizeChoice.Original else SizeChoice.Percent(percent)
}

/** The op that saves [choice]; null for `Original`, which saves no Resize at all. */
fun resizeOpFor(choice: SizeChoice): ToolOp? {
    val params = when (choice) {
        SizeChoice.Original -> return null
        is SizeChoice.Percent -> ResizeTool.defaults().with(ResizeTool.Scale, choice.percent)
        is SizeChoice.Custom -> ResizeTool.defaults()
            .with(ResizeTool.Width, choice.width)
            .with(ResizeTool.Height, choice.height)
            .with(ResizeTool.Proportional, choice.keepProportions)
    }
    return ToolOp(ResizeTool.manifest.id, params)
}

/**
 * This document with its Resize replaced by explicit [size] — a copy's (RD-401), which is
 * the copy's own and not what `Done` saved. Null [size] is the full frame: no Resize at all.
 */
fun EditDocument.sizedFor(size: IntSize?): EditDocument {
    val unsized = without(ResizeTool.manifest.id)
    val op = size?.let { resizeOpFor(SizeChoice.Custom(it.width, it.height, keepProportions = false)) }
    return if (op == null) unsized else unsized.copy(ops = unsized.ops + op)
}

/**
 * Explicit pixels as a stage: whatever frame it lands on is scaled to exactly [width] ×
 * [height], each axis on its own (RD-401). **`Framed`** (RD-205), because the scale is
 * `target / frame` and only the fold knows the frame after the crop and the turns.
 */
internal fun explicitResize(width: Int, height: Int): RasterStage? {
    if (width <= 0 || height <= 0) return null
    return RasterStage.Framed { frame ->
        if (frame.width <= 0 || frame.height <= 0) {
            RasterStage.Geometry(Matrix())
        } else {
            val matrix = Matrix().apply { scale(width / frame.width.toFloat(), height / frame.height.toFloat()) }
            RasterStage.Geometry(matrix, IntSize(width, height))
        }
    }
}
