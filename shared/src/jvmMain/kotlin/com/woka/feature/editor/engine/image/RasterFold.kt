package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp

/**
 * Document → [RasterRecipe]. Pure: no canvas, no bitmap, no Compose runtime.
 *
 * Separating this from the composable is what makes rendering testable at all —
 * every ordering and composition rule here is asserted in `RasterFoldTest` without
 * a window.
 *
 * The pending operation folds in exactly like a committed one. A preview that
 * rendered differently from what committing produces would make every slider lie
 * about its own result (§3.1).
 */
fun fold(
    document: EditDocument,
    registry: EditorToolRegistry,
    sourceSize: IntSize
): RasterRecipe {
    val skipped = mutableListOf<ToolOp>()
    val stages = orderedStages(document, registry, skipped)

    val matrix = Matrix()
    var outputSize = sourceSize
    var colorMatrix: ColorMatrix? = null
    val shaders = mutableListOf<RasterStage.Shader>()
    val paints = mutableListOf<RasterStage.Paint>()

    stages.forEach { stage ->
        when (stage) {
            is RasterStage.Geometry -> outputSize = applyGeometry(matrix, outputSize, stage)
            // Asked of the frame as it stands, then applied as any geometry is (RD-205).
            is RasterStage.Framed -> outputSize = applyGeometry(matrix, outputSize, stage.at(outputSize))
            is RasterStage.Pixels -> colorMatrix = multiply(colorMatrix, stage.colorMatrix)
            // Appended rather than merged: two convolutions do not compose into one,
            // so the order they were folded in is the order they run in (ME-165).
            is RasterStage.Shader -> shaders += stage
            is RasterStage.Paint -> paints += stage
            // Flattened before the walk, so this branch is unreachable and says so
            // rather than silently doing nothing if that ever stops being true.
            is RasterStage.Composite -> error("a composite reached the fold unflattened")
        }
    }

    return RasterRecipe(
        matrix = matrix,
        outputSize = outputSize,
        colorFilter = colorMatrix
            ?.takeUnless { it.isIdentity() }
            ?.let { ColorFilter.colorMatrix(it) },
        shaders = shaders,
        paints = paints,
        skipped = skipped
    )
}

/**
 * One geometry stage concatenated onto [matrix]; returns the frame after it.
 *
 * A stage that named its frame is taken at its word; one that did not has it derived from
 * the frame *as it stands*, which is what lets a second rotation know what the first left
 * behind (ME-105). The last geometry stage decides the frame: each one describes the size
 * *after* it applies, so a crop following a quarter turn wins over the turn's swapped
 * dimensions. One function for `Geometry` and `Framed` alike, so the two cannot differ.
 */
private fun applyGeometry(matrix: Matrix, frame: IntSize, stage: RasterStage.Geometry): IntSize {
    val (applied, next) = stage.outputSize
        ?.let { stage.matrix to it }
        ?: derivedFrame(frame, stage.matrix)
    concat(matrix, applied)
    return next
}
