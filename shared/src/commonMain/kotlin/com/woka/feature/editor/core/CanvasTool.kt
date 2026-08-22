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

    @Composable
    fun Overlay(scope: EditorToolScope)
}
