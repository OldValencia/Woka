package com.woka.feature.viewer.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ScalePreset

/** A resolved zoom change: where the scale lands and where the frame sits after it. */
internal data class ZoomResult(val sizeMode: SizeMode, val offsetX: Float, val offsetY: Float)

/** Keyboard zoom intents: `Ctrl +` / `Ctrl −` / `Ctrl 0` / `Ctrl 1`. */
enum class ZoomKey { In, Out, Fit, Actual }

/** Resolves a keyboard intent; the keys have no cursor, so nothing is anchored. */
internal fun resolveZoomKey(
    key: ZoomKey,
    mode: SizeMode,
    container: IntSize,
    native: IntSize,
    offsetX: Float,
    offsetY: Float
): ZoomResult {
    if (key == ZoomKey.Fit) return ZoomResult(SizeMode.Preset(ScalePreset.Fit), 0f, 0f)
    val bounds = zoomBounds(container, native)
    val percent = mode.percentIn(container, native)
    val target = when (key) {
        ZoomKey.In -> stepUp(percent, bounds)
        ZoomKey.Out -> stepDown(percent, bounds)
        else -> SizeMode.ACTUAL_PERCENT
    }
    return zoomTo(target, null, mode, container, native, offsetX, offsetY)
}

/**
 * Resolves a target percentage into a size state plus a corrected offset: snapped
 * onto a preset when it lands on one, anchored on [anchor] so the point under the
 * cursor stays put, and clamped so the frame cannot end up outside the panel.
 * Shared by the wheel, the double-tap and the keyboard so all three agree.
 */
internal fun zoomTo(
    percent: Int,
    anchor: Offset?,
    mode: SizeMode,
    container: IntSize,
    native: IntSize,
    offsetX: Float,
    offsetY: Float
): ZoomResult {
    val bounds = zoomBounds(container, native)
    val target = sizeModeFor(clampPercent(percent, bounds), container, native)
    val from = computeGraphicsScale(mode, container, native)
    val to = computeGraphicsScale(target, container, native)
    val moved = if (anchor != null) {
        zoomAround(anchor, container, from, to, offsetX, offsetY)
    } else {
        offsetX to offsetY
    }
    val (x, y) = clampOffset(moved.first, moved.second, target, container, native)
    return ZoomResult(target, x, y)
}

/** Applies a keyboard intent to the viewer state and flashes the HUD. */
internal fun MediaViewerState.applyZoomKey(key: ZoomKey, onSizeModeChange: (SizeMode) -> Unit) {
    val result = resolveZoomKey(key, sizeMode, containerSize, nativeSize, offsetX, offsetY)
    offsetX = result.offsetX
    offsetY = result.offsetY
    onSizeModeChange(result.sizeMode)
    zoomHudEvent.raise()
}
