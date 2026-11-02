package com.woka.support

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp

/**
 * An `EditorTimelineScope` over a document held in snapshot state — the session's
 * preview and commit, minus the session, so a band test can compose the strip alone and
 * read back what it wrote.
 *
 * [commits] counts history entries, which is how a test proves a gesture that changed
 * nothing pushed nothing.
 */
class FixtureTimeline(editing: Boolean = true) : EditorTimelineScope {

    var document by mutableStateOf(EditDocument())
        private set

    var commits = 0
        private set

    override var editing by mutableStateOf(editing)

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
}
