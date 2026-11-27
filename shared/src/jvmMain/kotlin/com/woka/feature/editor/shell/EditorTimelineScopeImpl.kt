package com.woka.feature.editor.shell

import androidx.compose.runtime.Stable
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.cancelPending
import com.woka.feature.editor.core.commit
import com.woka.feature.editor.core.copyFrame
import com.woka.feature.editor.core.focusClip
import com.woka.feature.editor.core.ownOpsOf
import com.woka.feature.editor.core.pasteOnClip
import com.woka.feature.editor.core.pickClip
import com.woka.feature.editor.core.preview
import com.woka.feature.editor.core.sharedOps

/**
 * `EditorTimelineScope`, assembled from the session (ME-210) — `EditorToolScopeImpl`'s
 * shape without the canvas geometry.
 *
 * **The cardinality is looked up from the op being committed**, not assumed: the band
 * may one day write for two tools (the audio row, ME-242), and a commit that guessed
 * `Single` for a `Repeating` tool would swallow its history. Unknown tools commit as
 * `Single`, which is what replaces rather than accumulates — the harmless answer.
 *
 * **The selection is the session's** (ME-221): on a film it is the scope and a step of
 * history, so it cannot live in the shell. [selected] is its value as this composition read it.
 *
 * **The tab and the shape are the screen's** (RD-301): [onOpenTool] and [shapes] are
 * `EditorScreen`'s own state handed in, so the timeline reaches the inspector without
 * learning that it exists.
 */
@Stable
internal class EditorTimelineScopeImpl(
    private val session: EditorSession,
    private val tools: List<EditorTool>,
    private val selected: String?,
    private val onOpenTool: (String, Set<ToolDomain>?) -> Unit = { _, _ -> },
    /** The canvas's selection (`EditorScreen`'s), which a Markup bar reads and writes. */
    private val shapes: ToolSelection? = null
) : EditorTimelineScope {

    override val selectedId: String? get() = selected

    override fun select(id: String?) = session.focusClip(id, session.frame)

    override fun pick(id: String?, key: Int, step: Boolean) {
        if (step) session.pickClip(id, key) else session.focusClip(id, key)
    }

    override fun preview(op: ToolOp) = session.preview(op)

    override fun commit() {
        val toolId = session.document.value.pending?.toolId ?: return
        val cardinality = tools.firstOrNull { it.manifest.id == toolId }
            ?.manifest?.cardinality ?: ToolCardinality.Single
        session.commit(cardinality)
    }

    override fun cancel() = session.cancelPending()

    override fun copyLayer(from: Int, to: Int) = session.copyFrame(from, to)

    override fun hasOwnLook(key: Int): Boolean = session.ownOpsOf(key).isNotEmpty()

    override fun ownOps(key: Int): List<ToolOp> = session.ownOpsOf(key)

    override fun pasteLayer(key: Int, ops: List<ToolOp>, taken: (ToolOp) -> Boolean) =
        session.pasteOnClip(key, ops, taken)

    override val openClip: Int? get() = session.frame.takeIf { it != NO_CLIP }

    override val sharedOps: List<ToolOp> get() = session.sharedOps

    override fun openTool(id: String, from: Set<ToolDomain>?) = onOpenTool(id, from)

    override fun selectShape(id: String) {
        shapes?.id = id
    }

    override val selectedShape: String? get() = shapes?.id

    override fun undo() = session.undo()
}
