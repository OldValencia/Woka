package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The whole edit, as data. The canvas renders this; no bitmap is mutated (§3.1).
 *
 * A few hundred bytes for any realistic session, which is what makes history a
 * list of snapshots rather than a stack of inverse commands — an undo that has to
 * know how to reverse every operation is an undo that is wrong for the one
 * operation nobody thought about.
 *
 * [pending] is the operation being dragged right now. It is held apart from [ops]
 * so that a slider can repaint at 60 fps without touching history: `preview`
 * replaces it, and only `commit` folds it in (§3.1). A document with a pending
 * operation renders exactly as if it were committed — the split is about undo
 * granularity, never about what is drawn.
 *
 * **Position in [ops] carries no meaning.** The fold sorts by `ToolDomain` rank,
 * not by the order the user clicked (§3.4), so appending is not a statement that
 * the operation applies last.
 */
@Immutable
@Serializable
data class EditDocument(
    val ops: List<ToolOp> = emptyList(),
    val pending: ToolOp? = null
) {

    val isEmpty: Boolean get() = ops.isEmpty() && pending == null

    /** Replaces the operation in flight. Never touches [ops], never history. */
    fun withPending(op: ToolOp): EditDocument = copy(pending = op)

    /**
     * Folds [pending] into [ops] per the tool's [cardinality], or returns the
     * document unchanged when there is nothing in flight — so a stray `commit`
     * cannot mint an empty history entry.
     */
    fun commitPending(cardinality: ToolCardinality): EditDocument {
        val op = pending ?: return this
        val next = when (cardinality) {
            ToolCardinality.Single -> ops.filterNot { it.toolId == op.toolId } + op
            ToolCardinality.Repeating -> ops + op
        }
        return copy(ops = next, pending = null)
    }

    /** Drops the operation in flight without applying it — an aborted drag. */
    fun clearPending(): EditDocument = copy(pending = null)

    fun opsOf(toolId: String): List<ToolOp> = ops.filter { it.toolId == toolId }
}
