package com.woka.feature.editor.core

import androidx.compose.runtime.Composable

/**
 * A tool that also needs direct manipulation on the canvas — crop, markup, trim.
 *
 * **Only three tools implement this**, and even they get their inspectors from
 * [EditorTool.params] like everything else; [Overlay] is the on-canvas dragging
 * and nothing more. Adjustments, filters, transform, resize, HDR and mute are pure
 * data plus one pure function, with no Compose code at all — that ratio is the
 * test of whether §3.4 is working.
 *
 * A separate interface rather than a nullable member on [EditorTool] so that
 * "needs an overlay" is answered by the type, not by a null check the shell would
 * have to repeat at every call site.
 */
interface CanvasTool : EditorTool {

    /**
     * Whether the canvas should draw the picture **without** this tool's own work while
     * the tool is open, because the overlay is already showing it.
     *
     * True for a crop: the rectangle is the result, drawn over the whole picture, and
     * folding it in as well makes the tool measure itself — the frame shrinks, the rect
     * is clamped into the smaller frame, and it shrinks again on the next event.
     *
     * False for markup, which is the case the default serves: a stroke has to be
     * visible on the canvas as it is drawn, and the overlay only adds handles to it.
     */
    val previewsThroughOverlay: Boolean get() = false

    /**
     * "This document holds something of mine that is worth selecting" (ME-149).
     *
     * Asked of every canvas tool when **no** tool is open, so the shell can mount one
     * anyway and let a drawn mark be moved without reopening the panel that covers it.
     * Defaulted false, which is Crop's answer — a crop rectangle is not an object on the
     * picture — and every future tool's until it says otherwise.
     *
     * **A tool answering true must leave [previewsThroughOverlay] false.** With no tool
     * open the canvas is drawn from the whole document, so a tool whose own work was
     * subtracted from it would offer nothing to select. That is the one invariant the
     * shell cannot check for itself.
     */
    fun selectableIn(document: EditDocument): Boolean = false

    /**
     * `Delete` or `Backspace`, while this tool is open (ME-114).
     *
     * Returns whether the press was *used*. Markup removes the selected shape and says
     * true; Crop has nothing to delete and says false, which is why this is defaulted
     * rather than added to both. The editor consumes the key either way — it owns the
     * keyboard while it is open, including for the presses it has no work for
     * (`EditorKeys`) — so the answer decides what happens, not whether the viewer
     * underneath also hears it.
     *
     * Not a `@Composable`: it is called from the key handler, which is an effect.
     */
    fun onDelete(scope: EditorToolScope): Boolean = false

    /**
     * A chance to apply an inspector edit to **what is selected on the canvas**, rather
     * than only to the tool's own parameters (ME-114, asked for 2026-08-24).
     *
     * Markup's `Color` and `Width` are the pen for the *next* shape, because each shape
     * stores its own style. That is right while nothing is selected and wrong the moment
     * something is: selecting an arrow and choosing red plainly means "make this one red",
     * and without this hook the shape sat there unchanged.
     *
     * Returns the rewritten parameter set, or null for "nothing to apply" — which is every
     * spec but two, every tool but one, and every moment with no selection. It runs after
     * [EditorTool.resolve] and its result is what reaches the document, so a restyle is
     * part of the same operation as the pen change and therefore the same undo step.
     *
     * [selectedId] is the *scope's* selection, never the document's: §3.1 keeps selection
     * out of the document so that clicking a shape records no history.
     */
    fun restyled(spec: ParamSpec<*>, params: ParamSet, selectedId: String?): ParamSet? = null

    /**
     * What the inspector should **show** while something is selected — the read-side twin
     * of [restyled] (reported by hand 2026-08-29).
     *
     * [restyled] made a control write to the selection and nothing made it *read* from it,
     * so the panel went on reporting the pen: a yellow arrow selected after the pen had
     * been set to blue showed blue, and a bold caption reselected after `Bold` was cleared
     * showed the chip unlit. Every control passes an absolute value, so a stale reading is
     * also a stale write waiting to happen — clicking the lit chip of a shape whose real
     * face is off would have turned it *on*.
     *
     * **Display only.** The document is untouched and no history entry exists: selecting a
     * shape records nothing (§3.1), and this is what keeps that true while still letting
     * the panel describe what is under the cursor.
     */
    fun displayed(params: ParamSet, selectedId: String?): ParamSet = params

    @Composable
    fun Overlay(scope: EditorToolScope)
}
