package com.woka.feature.editor.shell

import androidx.compose.runtime.Stable
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.cancelPending
import com.woka.feature.editor.core.commit
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
    override val editing: Boolean
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
}
