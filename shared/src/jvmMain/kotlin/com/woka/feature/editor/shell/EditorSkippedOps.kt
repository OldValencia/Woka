package com.woka.feature.editor.shell

import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp

/**
 * Operations the fold will not apply, derived by the shell rather than read out of
 * the engine.
 *
 * `EditorEngine` deliberately exposes no `recipe()` (§3.3): the moment the shell can
 * name `RasterRecipe`, every file touching it becomes a video ticket. But the
 * inspector still has to say "2 operations were not applied", so the rule is applied
 * a second time here, against the two things the shell already holds — the document
 * and the registry.
 *
 * **This must stay identical to `RasterFold.resolve`.** Both ask the same question:
 * is the owning tool missing, or present but switched off? A drift between them shows
 * up as a notice that lies, which is worse than no notice — §3.4's promise is that a
 * document never silently loses work, and the notice is how that promise is kept.
 *
 * [EditDocument.pending] is included for the same reason the fold includes it. In
 * practice it never contributes: a pending operation belongs to the tool the user has
 * open, and a disabled tool cannot be opened. It is here so the two derivations match
 * on inspection rather than by argument.
 */
internal fun skippedOps(
    document: EditDocument,
    registry: EditorToolRegistry,
    kind: MediaKind
): List<ToolOp> {
    val enabled = registry.tools(kind).value
    return (document.ops + listOfNotNull(document.pending)).filter { op ->
        val tool = registry.toolFor(op)
        tool == null || tool !in enabled
    }
}

/**
 * What the notice names, in document order and without repeats.
 *
 * An operation whose tool is **unregistered** contributes no title — there is nothing
 * truthful to call it. That is why the notice leads with the count and lists titles
 * second: the count is always right, and a document carrying an op from a tool this
 * build does not have still reports honestly instead of naming it "null".
 */
internal fun skippedToolTitles(
    skipped: List<ToolOp>,
    registry: EditorToolRegistry
): List<String> =
    skipped.mapNotNull { registry.toolFor(it)?.manifest?.title }.distinct()
