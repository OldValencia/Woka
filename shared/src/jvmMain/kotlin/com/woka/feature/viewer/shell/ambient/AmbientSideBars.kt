package com.woka.feature.viewer.shell.ambient

import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/**
 * Whether the drawn picture leaves the container's *sides* empty — a 16:9 file on a
 * wider panel, which is a monitor mismatch rather than anything the file asked for.
 * The top/bottom letterbox of 2.39:1 content is graded in and is not this.
 *
 * `ContentScale.Fit` sizes the picture, and `renderScale` is the graphics-layer
 * scale applied on top of it, so the drawn width is the fitted one times that.
 *
 * Beside [AmbientFillLayer] rather than in `MediaViewerContent`, which asks it: it is
 * the question that decides whether the fill is drawn at all, and moving it here is
 * what put that file back under the size mandate.
 */
internal fun hasSideBars(container: IntSize, native: IntSize, scale: Float): Boolean {
    if (container.width <= 0 || container.height <= 0) return false
    if (native.width <= 0 || native.height <= 0) return false
    val fit = min(
        container.width.toFloat() / native.width,
        container.height.toFloat() / native.height
    )
    return native.width * fit * scale < container.width - 2f
}
