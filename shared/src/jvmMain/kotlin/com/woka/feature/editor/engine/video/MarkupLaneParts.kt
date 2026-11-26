package com.woka.feature.editor.engine.video

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density

/*
 * What `MarkupLane`'s pointer asks of the bars, apart from the lane at the size mandate: which
 * bar and which part of it is under the pointer, and how a drag's badge reads.
 */

/** The bar under [at] and the part of it pressed: an end within 8 dp when the bar is wide enough. */
internal fun barAt(bars: List<MarkupBar>, geometry: StripGeometry, at: Offset, density: Density): Pair<MarkupBar, BarPart>? {
    val rowPx = with(density) { (TimelineTokens.markupBarHeight + TimelineTokens.markupRowGap).toPx() }
    val reach = with(density) { TimelineTokens.edgeReach.toPx() }
    val row = (at.y / rowPx).toInt()
    val bar = bars.lastOrNull { it.row == row && at.x in geometry.xOf(it.window.atMs)..geometry.xOf(it.window.endMs) } ?: return null
    val left = geometry.xOf(bar.window.atMs)
    val right = geometry.xOf(bar.window.endMs)
    val part = when {
        right - left < reach * 3f -> BarPart.Body
        at.x - left <= reach -> BarPart.Start
        right - at.x <= reach -> BarPart.End
        else -> BarPart.Body
    }
    return bar to part
}

/** A length in tenths, `2.4 s`; a start as the ruler would label it, `0:03.2`. */
internal fun lengthLabel(ms: Long): String = "${ms / 1000L}.${ms % 1000L / 100L} s"

internal fun startLabel(ms: Long): String = "${ms / 60_000L}:${(ms / 1000L % 60L).toString().padStart(2, '0')}.${ms % 1000L / 100L}"
