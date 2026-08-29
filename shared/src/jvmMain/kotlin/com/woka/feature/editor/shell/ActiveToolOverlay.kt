package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Matrix
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolScope
import com.woka.feature.editor.core.ToolCardinality

/**
 * Mounting a canvas tool's overlay, and building the scope it is handed.
 *
 * Split from `EditorToolScopeImpl` when ME-114's `Delete` registration took that file
 * past the size mandate. The seam is the one that was already there: the scope *type* and
 * the selection it holds on one side, the composables that build and mount one on this.
 */

/**
 * The open tool's overlay, when it has one (ME-109).
 *
 * **The type answers "needs an overlay", not a null check** — `CanvasTool` is a separate
 * interface for exactly this, so the shell asks once, here, and nothing else in the
 * editor repeats the question. A tool that is not one draws nothing and costs a cast.
 */
@Composable
internal fun ActiveToolOverlay(
    tool: EditorTool?,
    session: EditorSession,
    geometry: EditorCanvasGeometry,
    previewMatrix: Matrix,
    canvasKeys: CanvasKeys? = null,
    /** Owned by `EditorScreen`, because the inspector reads it too (ME-114). */
    selection: ToolSelection? = null
) {
    val canvasTool = tool as? CanvasTool ?: return
    val held = selection ?: remember(session) { ToolSelection() }
    val scope = rememberToolScope(
        session = session,
        cardinality = canvasTool.manifest.cardinality,
        geometry = geometry,
        previewMatrix = previewMatrix,
        selection = held,
        canvasKeys = canvasKeys
    )

    // The tool's `Delete` reaches the key handler through the one slot the editor has
    // (`CanvasKeys`), and gives it back on dispose — a handler left behind would delete
    // out of a tool that is no longer open (ME-114).
    if (canvasKeys != null) {
        DisposableEffect(canvasKeys, canvasTool, scope) {
            canvasKeys.onDelete = { canvasTool.onDelete(scope) }
            // And the keyboard goes back unconditionally: a tool torn down mid-edit must
            // not leave the editor deaf to `Esc` (ME-145).
            onDispose {
                canvasKeys.onDelete = null
                canvasKeys.typing = false
            }
        }
    }

    canvasTool.Overlay(scope)
}

/**
 * The scope for the tool currently open.
 *
 * Rebuilt whenever the document or the geometry moves — both are read by the overlay on
 * every frame, and a scope holding a stale one would draw against a fold that has since
 * moved on. The selection is handed in rather than made here, so it survives that — and
 * so the inspector can read the same one (ME-114).
 */
@Composable
internal fun rememberToolScope(
    session: EditorSession,
    cardinality: ToolCardinality,
    geometry: EditorCanvasGeometry,
    previewMatrix: Matrix,
    selection: ToolSelection,
    canvasKeys: CanvasKeys? = null
): EditorToolScope {
    val document by session.document.collectAsState()
    val values = previewMatrix.values.toList()

    return remember(session, cardinality, selection, canvasKeys, document, geometry, values) {
        // Source → what the engine drew → where the **picture** put it. Composed rather
        // than assumed identity: with a flip in the document the two disagree by the
        // width of the picture, and an overlay drawn without this lands on the far
        // side of it (2026-08-22).
        //
        // **`sourceToPicture`, never `sourceToCanvas`** (2026-08-29). `EditorCanvasHost`
        // composes this overlay inside the box it has already centred and panned, so the
        // layout applies that offset and a matrix carrying it too applies it twice — which
        // is a selection frame drifting off its own caption the moment the picture is
        // dragged. See `EditorCanvasGeometry.sourceToPicture` for why it hid for so long.
        val sourceToCanvas = previewMatrix.thenApplying(geometry.sourceToPicture)
        EditorToolScopeImpl(
            session = session,
            cardinality = cardinality,
            selection = selection,
            canvasKeys = canvasKeys,
            document = document,
            sourceSize = geometry.sourceSize,
            canvasToSource = sourceToCanvas.inverted(),
            sourceToCanvas = sourceToCanvas
        )
    }
}

/**
 * "Apply this, then [next]" — Compose's `timesAssign` computes `this = other * this`, so
 * the factor that acts first is the receiver (the same order `concat` documents in the
 * raster fold, and the same trap: written the other way round it composes the shift
 * before the turn).
 */
private fun Matrix.thenApplying(next: Matrix): Matrix {
    val composed = Matrix(values.copyOf())
    composed *= next
    return composed
}

private fun Matrix.inverted(): Matrix = Matrix(values.copyOf()).apply { invert() }
