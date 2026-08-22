package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolScope
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp

/**
 * What is highlighted right now, outliving the scopes that expose it.
 *
 * Selection is deliberately not in the document (§3.1) — clicking a markup shape must
 * not produce an undo step — and it must not be in the scope object either, because a
 * scope is rebuilt on every document and geometry change and would forget it several
 * times a second. It lives as long as the tool is open, which is what this holds.
 */
@Stable
internal class ToolSelection {
    var id: String? by mutableStateOf(null)
}

/**
 * `EditorToolScope`, assembled from the session and the canvas geometry (ME-109).
 *
 * A separate file because `EditorCanvasHost` is at the size mandate, and a separate
 * class because the scope is what a `CanvasTool`'s overlay is handed.
 *
 * **Immutable, and rebuilt rather than mutated**: a scope carrying mutable state is one
 * an overlay could observe mid-change.
 *
 * **The cardinality is the tool's own**, carried here rather than assumed: a crop is
 * `Single` and replaces itself, markup will be `Repeating` and accumulate, and it is
 * the commit — not the tool — that has to know which.
 */
@Stable
internal class EditorToolScopeImpl(
    private val session: EditorSession,
    private val cardinality: ToolCardinality,
    private val selection: ToolSelection,
    override val document: EditDocument,
    override val sourceSize: IntSize,
    override val canvasToSource: Matrix,
    override val sourceToCanvas: Matrix
) : EditorToolScope {

    override val selectedId: String? get() = selection.id

    override fun select(id: String?) {
        selection.id = id
    }

    override fun preview(op: ToolOp) = session.preview(op)

    override fun commit() = session.commit(cardinality)
}

/**
 * What the canvas draws: [document], minus the open tool's own work when that tool
 * shows its result itself (`CanvasTool.previewsThroughOverlay`, ME-109).
 *
 * A crop folded into the picture underneath its own rectangle is a crop measuring
 * itself — the frame shrinks, the rect is clamped into it, and it shrinks again.
 */
internal fun canvasDocumentFor(tool: EditorTool?, document: EditDocument): EditDocument =
    (tool as? CanvasTool)
        ?.takeIf { it.previewsThroughOverlay }
        ?.let { document.without(it.manifest.id) }
        ?: document

/**
 * The open tool's overlay, when it has one (ME-109).
 *
 * **The type answers "needs an overlay", not a null check** — `CanvasTool` is a
 * separate interface for exactly this, so the shell asks once, here, and nothing else
 * in the editor repeats the question. A tool that is not one draws nothing and costs a
 * cast.
 */
@Composable
internal fun ActiveToolOverlay(
    tool: EditorTool?,
    session: EditorSession,
    geometry: EditorCanvasGeometry,
    previewMatrix: Matrix
) {
    val canvasTool = tool as? CanvasTool ?: return
    canvasTool.Overlay(
        rememberToolScope(session, canvasTool.manifest.cardinality, geometry, previewMatrix)
    )
}

/**
 * The scope for the tool currently open.
 *
 * Rebuilt whenever the document or the geometry moves — both are read by the overlay on
 * every frame, and a scope holding a stale one would draw against a fold that has since
 * moved on. The selection is keyed on the session alone, so it survives that.
 */
@Composable
internal fun rememberToolScope(
    session: EditorSession,
    cardinality: ToolCardinality,
    geometry: EditorCanvasGeometry,
    previewMatrix: Matrix
): EditorToolScope {
    val document by session.document.collectAsState()
    val selection = remember(session) { ToolSelection() }
    val values = previewMatrix.values.toList()

    return remember(session, cardinality, selection, document, geometry, values) {
        // Source → what the engine drew → where the canvas put it. Composed rather
        // than assumed identity: with a flip in the document the two disagree by the
        // width of the picture, and an overlay drawn without this lands on the far
        // side of it (2026-08-22).
        val sourceToCanvas = previewMatrix.thenApplying(geometry.sourceToCanvas)
        EditorToolScopeImpl(
            session = session,
            cardinality = cardinality,
            selection = selection,
            document = document,
            sourceSize = geometry.sourceSize,
            canvasToSource = sourceToCanvas.inverted(),
            sourceToCanvas = sourceToCanvas
        )
    }
}

/**
 * "Apply this, then [next]" — Compose's `timesAssign` computes `this = other * this`,
 * so the factor that acts first is the receiver (the same order `concat` documents in
 * the raster fold, and the same trap: written the other way round it composes the
 * shift before the turn).
 */
private fun Matrix.thenApplying(next: Matrix): Matrix {
    val composed = Matrix(values.copyOf())
    composed *= next
    return composed
}

private fun Matrix.inverted(): Matrix = Matrix(values.copyOf()).apply { invert() }
