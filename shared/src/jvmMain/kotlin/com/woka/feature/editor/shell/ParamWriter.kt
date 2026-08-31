package com.woka.feature.editor.shell

import androidx.compose.runtime.Stable
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.ToolInteraction
import androidx.compose.ui.unit.IntSize
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
    /**
     * The frame the picture currently has, for the one thing a tool cannot work out
     * for itself: turning "long edge 2560" into a scale (`EditorTool.resolve`, ME-110).
     * Zero before the canvas has measured, which resolves to nothing and writes the
     * plain value.
     */
    private val frame: IntSize = IntSize.Zero,
    /**
     * What the canvas has selected, for the one hook that needs it:
     * `CanvasTool.restyled` (ME-114). Null for every tool without an overlay, and for an
     * overlay with nothing selected.
     *
     * **Read at the moment of the write, exactly like the document below** — for the same
     * reason, and by symmetry with it rather than in answer to a measured failure
     * (2026-08-26).
     */
    private val selectedId: () -> String? = { null },
    /** The source's own dimensions, for the seed a write starts from — see `paramsOf`. */
    private val sourceSize: IntSize = IntSize.Zero
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
        // **Read now, not at composition.** `DetentSlider` keeps its gesture lambda in a
        // `pointerInput` keyed on the track's width, so a writer holding a captured
        // document goes on writing against the document as it was when that gesture was
        // set up — and every drag rebuilt its parameters from a stale base, wiping the
        // slider moved before it (2026-08-22).
        val live = session.document.value
        val base = if (spec is ActionParam) tool.defaults(sourceSize) else paramsOf(live, tool, sourceSize)
        val written = base.with(spec, value)
        // A tool may rewrite its own parameters now that the frame is known — a preset
        // that means "this many pixels on the long edge" resolves to a scale here, so
        // the document stores the number and `stage()` never needs the frame (§3.4).
        val resolved = tool.resolve(spec, written, frame) ?: written
        // And a canvas tool may apply the same edit to what is *selected* — choosing red
        // with an arrow selected means "make this one red", not only "draw the next one
        // red" (ME-114). Part of the same operation, so it is the same undo step.
        val params = (tool as? CanvasTool)?.restyled(spec, resolved, selectedId()) ?: resolved
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
