package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine

/** Where a `CanvasTool`'s overlay is drawn, once one exists (ME-109, ME-113). */
internal const val CANVAS_HOST_TAG = "editor-canvas"

/**
 * The frame the medium draws itself into, and the magnifier over it.
 *
 * It measures, works out where the picture lands, and composes [EditorEngine.Surface]
 * — **it never names a bitmap, a player or a recipe**, which is what lets the video
 * editor reuse this file rather than acquire its own (§3.3).
 *
 * Zoom runs Fit to 1000 %, the range the viewer offers. Above 100 % the ≤2560 px proxy
 * is soft — ME-116 sharpens it by drawing the native bitmap for the visible region —
 * but a magnifier that refuses to magnify is the worse of the two (2026-08-18).
 *
 * [resetKey] returns the view to Fit whenever it changes — the active tool, in
 * practice. A crop opened at 400 % on a corner of the picture is a crop nobody can
 * frame (§4.3).
 */
@Composable
internal fun EditorCanvasHost(
    engine: EditorEngine,
    document: EditDocument,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    /** Laid on the picture itself — a turn or a flip playing (`CanvasMotionState`). */
    picture: Modifier = Modifier,
    overlay: @Composable (EditorCanvasGeometry) -> Unit = {}
) {
    // The size from the constraints, in the first pass — not from `onSizeChanged`, a frame
    // later: that frame was an empty room between the viewer and the editor (2026-11-25).
    BoxWithConstraints(modifier.fillMaxSize()) {
        CanvasHostBody(engine, document, IntSize(constraints.maxWidth, constraints.maxHeight), resetKey, picture, overlay)
    }
}
