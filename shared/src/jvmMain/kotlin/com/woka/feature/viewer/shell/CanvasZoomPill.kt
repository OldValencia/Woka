package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.woka.app.shell.CanvasZoom
import com.woka.app.shell.LocalCanvasOverride
import com.woka.feature.viewer.shell.zoom.SizeControl

/**
 * The size control, pointed at whoever has taken the canvas over — the editor, drawn
 * over a viewer that is still mounted and frozen underneath (§4.1).
 *
 * It speaks percent of native 1:1, which is the scale `ZoomScale.kt` already works in,
 * so the pill needs no second model of what "zoomed in" means and the two owners cannot
 * disagree about what 100 % is.
 */
@Composable
internal fun CanvasZoomControl(zoom: CanvasZoom) {
    SizeControl(
        sizeMode = sizeModeFor(zoom.percent, zoom.containerSize, zoom.nativeSize),
        containerSize = zoom.containerSize,
        nativeSize = zoom.nativeSize,
        onSizeModeChange = { zoom.onPercent(it.percentIn(zoom.containerSize, zoom.nativeSize)) },
        onOffsetReset = zoom.onReset,
        bounds = zoom.minPercent..zoom.maxPercent
    )
}

/**
 * The whole pill, for an owner that has no viewer under it to borrow one from —
 * the editor opened straight from the grid.
 *
 * Info is deliberately absent rather than stubbed: the dialog belongs to the viewer,
 * and a button that looks live and does nothing is the thing
 * `MediaViewerFullscreenControls` already refuses to ship.
 */
@Composable
fun CanvasZoomPill(modifier: Modifier = Modifier) {
    val zoom = LocalCanvasOverride.current.zoom ?: return
    MediaViewerControlsContainer(modifier) {
        CanvasZoomControl(zoom)
        PillGap()
        PillDivider()
        PillGap()
        HdrModeControl()
    }
}
