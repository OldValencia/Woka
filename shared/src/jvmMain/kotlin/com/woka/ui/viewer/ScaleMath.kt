package com.woka.ui.viewer

import androidx.compose.ui.unit.IntSize
import com.woka.data.ScalePreset

/**
 * Multiplier applied to a `ContentScale.Fit` image (which is already drawn at
 * `native * fitScale`). The zoom percentage is relative to the fitted baseline:
 * 100% == Fit == fills the window with letterboxing, never larger. Fill covers
 * the window (cropping the overflow); Original ("Actual") is native 1:1 pixels.
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
        is SizeMode.Zoom -> sizeMode.percent / 100f
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
