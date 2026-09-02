package com.woka.feature.editor.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * `Single` and replaces itself, a rotation is `Repeating` and accumulates, markup is
 * `Single` and previews a whole new shape list (ME-113) — the commit has to know which.
 */
@Stable
internal class EditorToolScopeImpl(
    private val session: EditorSession,
    private val cardinality: ToolCardinality,
    private val selection: ToolSelection,
    /**
     * The editor's one keyboard slot, so [captureKeys] can reach it (ME-145). Null in the
     * tests that compose an overlay without a host, where nothing owns the keyboard to
     * stand aside from.
     */
    private val canvasKeys: CanvasKeys?,
    override val document: EditDocument,
    override val sourceSize: IntSize,
    override val canvasToSource: Matrix,
    override val sourceToCanvas: Matrix,
    /** Mounted because the tool owns a mark, not because it was opened (ME-149). */
    override val selectOnly: Boolean = false
) : EditorToolScope {

    override val selectedId: String? get() = selection.id

    override fun select(id: String?) {
        selection.id = id
    }

    override fun preview(op: ToolOp) = session.preview(op)

    override fun commit() = session.commit(cardinality)

    override fun cancel() = session.cancelPending()

    override fun captureKeys(captured: Boolean) {
        canvasKeys?.typing = captured
    }
}

/**
 * What the canvas draws: [document], minus the open tool's own work when that tool
 * shows its result itself (`CanvasTool.previewsThroughOverlay`, ME-109).
 *
 * A crop folded into the picture underneath its own rectangle is a crop measuring
 * itself — the frame shrinks, the rect is clamped into it, and it shrinks again.
 */
internal fun canvasDocumentFor(
    tool: EditorTool?,
    document: EditDocument,
    peeking: Boolean = false
): EditDocument {
    // ME-173: the whole original, geometry included, so the frame returns to the
    // source's own dimensions. Nothing is written and no op is dropped — the caller
    // still holds the document, and releasing the button asks for it again.
    if (peeking) return EditDocument()
    return (tool as? CanvasTool)
        ?.takeIf { it.previewsThroughOverlay }
        ?.let { document.without(it.manifest.id) }
        ?: document
}
