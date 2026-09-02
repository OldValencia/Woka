package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTool
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
            is RasterStage.Geometry -> {
                // A stage that named its frame is taken at its word; one that did not
                // has it derived from the frame *as it stands*, which is what lets a
                // second rotation know what the first left behind (ME-105).
                val (applied, frame) = stage.outputSize
                    ?.let { stage.matrix to it }
                    ?: derivedFrame(outputSize, stage.matrix)
                concat(matrix, applied)
                // The last geometry stage decides the frame: each one describes the
                // size *after* it applies, so a crop following a quarter turn wins
                // over the turn's swapped dimensions.
                outputSize = frame
            }
            is RasterStage.Pixels -> colorMatrix = multiply(colorMatrix, stage.colorMatrix)
            // Appended rather than merged: two convolutions do not compose into one,
            // so the order they were folded in is the order they run in (ME-165).
            is RasterStage.Shader -> shaders += stage
            is RasterStage.Paint -> paints += stage
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
 * The operations a document currently *means*: committed, with the one in flight folded
 * in **exactly as committing it would** (2026-08-22).
 *
 * `ops + pending` was wrong for every `Single` tool. A crop or an adjustment already in
 * the document, plus a pending one from the same tool, applied **both** — so dragging
 * Tint from −100 to +100 previewed as roughly neutral and jumped to green on release.
 * §3.1's promise is that a preview renders what committing produces, and the only way to
 * keep that honest is to ask the same function: `commitPending`, with the tool's own
 * cardinality.
 *
 * A tool the registry cannot resolve keeps the old behaviour — it is on its way to
 * [skipped] regardless, and dropping it here would lose the report.
 */
private fun applied(document: EditDocument, registry: EditorToolRegistry): List<ToolOp> {
    val pending = document.pending ?: return document.ops
    val cardinality = registry.toolFor(pending)?.manifest?.cardinality
        ?: return document.ops + pending
    return document.commitPending(cardinality).ops
}

/**
 * Operations in **application** order, not click order (§3.4).
 *
 * `sortedBy` is stable, which is the whole point: ranking is by domain, so two
 * operations of the same domain keep the order the user made them in. An unstable
 * sort would let two arrows swap places between runs of the same document.
 *
 * An operation whose tool is unregistered or switched off contributes no stage and
 * lands in [skipped] instead — never an exception, and never a silently dropped
 * edit. A tool returning `null` from `stage()` is different: that is a no-op
 * configuration, not a missing tool, so it is dropped without being reported.
 */
private fun orderedStages(
    document: EditDocument,
    registry: EditorToolRegistry,
    skipped: MutableList<ToolOp>
): List<RasterStage> {
    // The raster fold is the image engine's, so enablement is asked for images.
    val enabled = registry.tools(MediaKind.Image).value
    return applied(document, registry)
        .mapNotNull { op -> resolve(op, registry, enabled, skipped) }
        .sortedBy { (_, tool) -> tool.manifest.domain.ordinal }
        .mapNotNull { (op, tool) -> tool.stage(op.params) as? RasterStage }
}

private fun resolve(
    op: ToolOp,
    registry: EditorToolRegistry,
    enabled: List<EditorTool>,
    skipped: MutableList<ToolOp>
): Pair<ToolOp, EditorTool>? {
    val tool = registry.toolFor(op)
    if (tool == null || tool !in enabled) {
        skipped += op
        return null
    }
    return op to tool
}
