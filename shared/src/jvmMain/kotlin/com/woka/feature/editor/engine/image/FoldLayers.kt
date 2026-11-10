package com.woka.feature.editor.engine.image

import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp

/**
 * A `Single` tool's **last** op, and only that, for every such tool in [resolved] (review
 * 1.0.7, F1); a `Repeating` tool's ops all stay.
 *
 * One document holds one op per `Single` tool — `commitPending` sees to it — until two layers
 * are composed: the film's Adjust, then a clip's own. The clip's replaces the film's (the
 * author, 2026-11-10), which is what the open clip already showed, since committing it dropped
 * the film's from that document. Composed elsewhere — the player, the grid, the render, the
 * clip reopened — both applied, and the clip played with its exposure doubled.
 *
 * Here rather than at every composition, because the fold is the one place they all reach.
 */
internal fun lastOfEachSingle(resolved: List<Pair<ToolOp, EditorTool>>): List<Pair<ToolOp, EditorTool>> =
    resolved.filterIndexed { at, (op, tool) ->
        tool.manifest.cardinality == ToolCardinality.Repeating ||
            resolved.drop(at + 1).none { it.first.toolId == op.toolId }
    }
