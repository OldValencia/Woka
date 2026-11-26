package com.woka.support

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp

/**
 * An `EditorTimelineScope` over a document held in snapshot state — the session's
 * preview and commit, minus the session, so a timeline test can compose the lanes alone and
 * read back what it wrote — and what it asked of the inspector: the tool it opened, the shape
 * it selected (RD-301).
 *
 * [commits] counts history entries, which is how a test proves a gesture that changed
 * nothing pushed nothing.
 */
class FixtureTimeline : EditorTimelineScope {

    var document by mutableStateOf(EditDocument())
        private set

    var commits = 0
        private set

    override var selectedId: String? by mutableStateOf(null)
        private set

    override fun select(id: String?) {
        selectedId = id
    }

    override fun preview(op: ToolOp) {
        document = document.withPending(op)
    }

    override fun commit() {
        if (document.pending == null) return
        document = document.commitPending(ToolCardinality.Single)
        commits++
    }

    override fun cancel() {
        document = document.clearPending()
    }

    /** The last tool asked for, with the domains it was asked from — `null` for any. */
    var opened: Pair<String, Set<ToolDomain>?>? = null
        private set

    override fun openTool(id: String, from: Set<ToolDomain>?) {
        opened = id to from
    }

    override var selectedShape: String? by mutableStateOf(null)
        private set

    override fun selectShape(id: String) {
        selectedShape = id
    }

    /** Each clip's own work by layer key — what `Copy look` takes. */
    var own: Map<Int, List<ToolOp>> = emptyMap()

    override fun ownOps(key: Int): List<ToolOp> = own[key].orEmpty()

    /** The last paste: the layer it went to and the ops it took. */
    var pasted: Pair<Int, List<ToolOp>>? = null
        private set

    override fun pasteLayer(key: Int, ops: List<ToolOp>, taken: (ToolOp) -> Boolean) {
        pasted = key to ops.filter(taken)
    }

    /** Starts the fixture from [document] — a film with Markup shapes already on it. */
    fun load(document: EditDocument) {
        this.document = document
    }
}
