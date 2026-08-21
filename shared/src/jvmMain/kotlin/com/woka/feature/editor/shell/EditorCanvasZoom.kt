package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.woka.app.shell.CanvasZoom
import com.woka.app.shell.LocalCanvasOverride
import kotlin.math.roundToInt

/**
 * Hands the canvas's zoom to the top bar's pill, and takes it back on the way out.
 *
 * The editor draws over a viewer that is still mounted, so the pill up there is the
 * viewer's — see `CanvasOverrideState` for why it is borrowed rather than replaced.
 * The translation is from the editor's *multiplier over Fit* to the percent of native
 * 1:1 everything else in the app is written in.
 *
 * The published bounds are Fit to 1000 %, which is where `EditorCanvasGeometry` actually
 * stops. The ceiling matches the viewer's; the floor does not, because the editor has one
 * mode and showing it letterboxed inside its own canvas is not a state worth reaching.
 */
@Composable
internal fun PublishCanvasZoom(
    geometry: EditorCanvasGeometry,
    onZoom: (Float) -> Unit,
    onReset: () -> Unit
) {
    val override = LocalCanvasOverride.current
    val curOnZoom by rememberUpdatedState(onZoom)
    val curOnReset by rememberUpdatedState(onReset)

    val fit = geometry.fitScale
    val published = remember(fit, geometry.scale, geometry.canvasSize, geometry.sourceSize) {
        val fitPercent = percentOf(fit)
        CanvasZoom(
            percent = percentOf(geometry.scale),
            minPercent = minOf(fitPercent, percentOf(fit * geometry.minZoom)),
            maxPercent = maxOf(fitPercent, percentOf(fit * geometry.maxZoom)),
            containerSize = geometry.canvasSize,
            nativeSize = geometry.sourceSize,
            // The host clamps: this is a request, and the floor is the viewer's.
            onPercent = { percent -> curOnZoom(percent / 100f / fit) },
            onReset = { curOnReset() }
        )
    }

    // Written after composition rather than during it, and cleared on the way out so
    // the pill goes back to driving the viewer it has been sitting above all along.
    SideEffect { override.zoom = published }
    DisposableEffect(Unit) {
        onDispose { override.zoom = null }
    }
}

private fun percentOf(scale: Float): Int = (scale * 100f).roundToInt().coerceAtLeast(1)
