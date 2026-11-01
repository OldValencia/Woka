package com.woka.app.shell.nowplaying

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.WindowCorner

/**
 * Where the bubble's anchor sits in [corner] of a [container]: [edge] from the sides
 * and the bottom, and [top] down from the window's top edge, so a top corner sits
 * under the title bar rather than over its buttons (`now-playing.html`, `.bub`).
 */
internal fun cornerOffset(
    corner: WindowCorner,
    container: IntSize,
    bubble: Int,
    edge: Int,
    top: Int
): IntOffset {
    val upper = corner == WindowCorner.TopLeft || corner == WindowCorner.TopRight
    val x = if (corner.isLeft) edge else container.width - edge - bubble
    val y = if (upper) top else container.height - edge - bubble
    return IntOffset(x, y)
}

/** The corner whose quadrant [center] falls in — where a released drag snaps to. */
internal fun nearestCorner(center: Offset, container: IntSize): WindowCorner {
    val left = center.x < container.width / 2f
    val upper = center.y < container.height / 2f
    return when {
        upper && left -> WindowCorner.TopLeft
        upper -> WindowCorner.TopRight
        left -> WindowCorner.BottomLeft
        else -> WindowCorner.BottomRight
    }
}

internal val WindowCorner.isLeft: Boolean
    get() = this == WindowCorner.TopLeft || this == WindowCorner.BottomLeft
