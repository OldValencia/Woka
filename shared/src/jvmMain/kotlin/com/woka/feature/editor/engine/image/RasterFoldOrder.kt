package com.woka.feature.editor.engine.image

import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp

/**
 * Which stages a document folds into, and in what order — split from `RasterFold` by RD-205,
 * which needed room there for `RasterStage.Framed`. The seam: this file decides **what** is
 * applied and when, `fold` decides **what it does** to the frame.
 */

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
 * `skipped` regardless, and dropping it here would lose the report.
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
 * `sortedWith` is stable, which is the whole point: ranking is by domain, so two
 * operations of the same domain keep the order the user made them in. An unstable
 * sort would let two arrows swap places between runs of the same document.
 *
 * **`foldsFirst` ranks inside the domain** (RD-205): a straighten belongs to the original
 * picture, so it applies before every turn and flip whatever order they were made in — a
 * flip after levelling mirrors a level picture and leaves the angle as written. Every
 * other tool ranks equal, so every document without one folds exactly as it did.
 *
 * An operation whose tool is unregistered or switched off contributes no stage and
 * lands in [skipped] instead — never an exception, and never a silently dropped
 * edit. A tool returning `null` from `stage()` is different: that is a no-op
 * configuration, not a missing tool, so it is dropped without being reported.
 */
internal fun orderedStages(
    document: EditDocument,
    registry: EditorToolRegistry,
    skipped: MutableList<ToolOp>
): List<RasterStage> {
    // The raster fold is the image engine's, so enablement is asked for images.
    val enabled = registry.tools(MediaKind.Image).value
    return applied(document, registry)
        .mapNotNull { op -> resolve(op, registry, enabled, skipped) }
        .let { lastOfEachSingle(it) }
        .sortedWith(
            compareBy(
                { (_, tool) -> tool.manifest.domain.ordinal },
                { (_, tool) -> if (tool.manifest.foldsFirst) 0 else 1 }
            )
        )
        .mapNotNull { (op, tool) -> tool.stage(op.params) as? RasterStage }
        .flatMap { flatten(it) }
}

/**
 * A [RasterStage.Composite] is its own contents, in order (ME-162).
 *
 * Recursive rather than one level deep, because nothing stops a composite holding a
 * composite and a fold that silently ignored the inner one would drop an edit — which
 * §3.4 promises never happens.
 */
private fun flatten(stage: RasterStage): List<RasterStage> = when (stage) {
    is RasterStage.Composite -> stage.stages.flatMap { flatten(it) }
    else -> listOf(stage)
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
