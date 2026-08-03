package com.woka.ui.viewer

import androidx.compose.ui.unit.IntSize
import com.woka.data.ScalePreset

/**
 * Multiplier applied to a `ContentScale.Fit` image (which is already drawn at
 * `native * fitScale`). The zoom percentage is relative to **native 1:1 pixels**,
 * so 100% == Original ("Actual"); Fit is whatever percentage happens to fit the
 * panel, and Fill covers it (cropping the overflow). The preset branches divide by
 * the un-rounded `fitScale` rather than going through the integer percentage —
 * "Actual" has to be pixel-exact, and at small fit percentages the rounding is
 * worth several percent.
 */
internal fun computeGraphicsScale(sizeMode: SizeMode, container: IntSize, native: IntSize): Float {
    if (container == IntSize.Zero || native.width <= 0 || native.height <= 0) return 1f
    val cw = container.width.toFloat()
    val ch = container.height.toFloat()
    val nw = native.width.toFloat()
    val nh = native.height.toFloat()
    val fitScale = minOf(cw / nw, ch / nh)
    if (fitScale <= 0f) return 1f
    return when (sizeMode) {
        // percent is of native, and the image already carries fitScale.
        is SizeMode.Zoom -> (sizeMode.percent / 100f) / fitScale
        is SizeMode.Preset -> when (sizeMode.preset) {
            ScalePreset.Fit -> 1f
            ScalePreset.Fill -> maxOf(cw / nw, ch / nh) / fitScale
            // "Actual" = native 1:1 pixels; the Fit image is native*fitScale, so undo it.
            ScalePreset.Original -> 1f / fitScale
        }
    }
}

/** True when the media, at [sizeMode], is larger than the [container] on either axis (so it can be panned). */
internal fun contentOverflows(sizeMode: SizeMode, container: IntSize, native: IntSize): Boolean {
    if (container == IntSize.Zero || native.width <= 0 || native.height <= 0) return false
    val cw = container.width.toFloat()
    val ch = container.height.toFloat()
    val fitScale = minOf(cw / native.width.toFloat(), ch / native.height.toFloat())
    val gl = computeGraphicsScale(sizeMode, container, native)
    val displayedW = native.width * fitScale * gl
    val displayedH = native.height * fitScale * gl
    return displayedW > cw + 0.5f || displayedH > ch + 0.5f
}
