package com.woka.feature.editor.shell

import androidx.compose.runtime.Stable
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.core.ToolOp

/**
 * The one place a control turns into an operation.
 *
 * **Undo granularity is decided here and nowhere else.** A row says only *what*
 * changed and whether the gesture has finished; whether that becomes a preview or a
 * history entry comes from `manifest.interaction`, which the tool declared. That is
 * what makes §3.4's promise mechanical rather than a matter of every renderer
 * remembering to be careful — a slider physically cannot mint two hundred undo steps,
 * because the code that would do it does not exist.
 */
@Stable
internal class ParamWriter(
    private val session: EditorSession,
    private val tool: EditorTool,
    private val document: EditDocument
) {

    /**
     * Writes [value] for [spec].
     *
     * [settled] is the gesture's own state: false while a knob is moving, true when it
     * is released. A control with no gesture — a switch, a segment, an action button —
     * passes true, and for an `Instant` tool the distinction never arises anyway.
     */
    fun <T> edit(spec: ParamSpec<T>, value: T, settled: Boolean = true) {
        // An `ActionParam` records *this happened*, never *the state is now* — which
        // is what its own contract says and what `Repeating` means. Building it from
        // the previous operation would carry that operation's flag forward, so
        // pressing Rotate and then Flip would produce one op claiming both and the
        // picture would rotate twice (ME-105).
        val base = if (spec is ActionParam) tool.defaults() else paramsOf(document, tool)
        val params = base.with(spec, value)
        session.preview(ToolOp(tool.manifest.id, params))

        val commit = when (tool.manifest.interaction) {
            // Nothing to drag, so the press is the commit.
            ToolInteraction.Instant -> true
            // Preview while moving, one entry on release.
            ToolInteraction.Continuous -> settled
            // The overlay owns the canvas gesture; the inspector's own rows behave
            // like any other control, so a crop's aspect chip commits on click while
            // the rect being dragged does not.
            ToolInteraction.Canvas -> settled
        }
        if (commit) session.commit(tool.manifest.cardinality)
    }

    /**
     * Ends a gesture, folding whatever was last previewed into one history entry.
     *
     * Separate from [edit] because a drag's last frame has already written the value
     * it wants — re-sending it on release would mean reading the value back out of a
     * composition that has not recomposed yet, which is how a released slider ends up
     * committing the value it had two frames ago.
     */
    fun settle() = session.commit(tool.manifest.cardinality)

    /**
     * Abandons a gesture without applying it — the escape hatch a drag needs and a
     * click never uses.
     */
    fun cancel() = session.cancelPending()
}
