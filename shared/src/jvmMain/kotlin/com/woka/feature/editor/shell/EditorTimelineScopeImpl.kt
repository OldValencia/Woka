package com.woka.feature.editor.shell

import androidx.compose.runtime.Stable
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.cancelPending
import com.woka.feature.editor.core.commit
import com.woka.feature.editor.core.copyFrame
import com.woka.feature.editor.core.moveToFrame
import com.woka.feature.editor.core.ownOpsOf
import com.woka.feature.editor.core.pasteOnClip
import com.woka.feature.editor.core.preview

/**
 * `EditorTimelineScope`, assembled from the session (ME-210) — `EditorToolScopeImpl`'s
 * shape without the canvas geometry.
 *
 * **The cardinality is looked up from the op being committed**, not assumed: the band
 * may one day write for two tools (the audio row, ME-242), and a commit that guessed
 * `Single` for a `Repeating` tool would swallow its history. Unknown tools commit as
 * `Single`, which is what replaces rather than accumulates — the harmless answer.
 *
 * [selection] is the band's own `ToolSelection`, never the canvas's (see the interface).
 */
@Stable
internal class EditorTimelineScopeImpl(
    private val session: EditorSession,
    private val tools: List<EditorTool>,
    private val selection: ToolSelection,
    override val editing: Boolean,
    override val perClip: Boolean = false
) : EditorTimelineScope {

    override val selectedId: String? get() = selection.id

    override fun select(id: String?) {
        selection.id = id
    }

    override fun preview(op: ToolOp) = session.preview(op)

    override fun commit() {
        val toolId = session.document.value.pending?.toolId ?: return
        val cardinality = tools.firstOrNull { it.manifest.id == toolId }
            ?.manifest?.cardinality ?: ToolCardinality.Single
        session.commit(cardinality)
    }

    override fun cancel() = session.cancelPending()

    override fun focus(key: Int) {
        if (perClip) session.moveToFrame(key, EditScope.SingleFrame)
    }

    override fun copyLayer(from: Int, to: Int) = session.copyFrame(from, to)

    override fun hasOwnLook(key: Int): Boolean = session.ownOpsOf(key).isNotEmpty()

    override fun ownOps(key: Int): List<ToolOp> = session.ownOpsOf(key)

    override fun pasteLayer(key: Int, ops: List<ToolOp>, taken: (ToolOp) -> Boolean) =
        session.pasteOnClip(key, ops, taken)

    override val openClip: Int? get() = session.frame.takeIf { perClip }
}
