package com.woka.core.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.v2.ScrollbarAdapter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The [ScrollbarAdapter] behind [rememberKnownLayoutScrollbarAdapter]: it answers
 * the scrollbar's three questions from [GridLineMetrics] instead of from Compose's
 * visible-row average. The reasoning for computing rather than estimating is with
 * the factory; the arithmetic is in the metrics (§11).
 *
 * Every entry point re-measures first, because a query can arrive at any point in
 * a scroll and the heights are only learned as rows appear.
 */
internal class KnownLayoutGridScrollbarAdapter(
    private val gridState: LazyGridState,
    lines: () -> List<GridLine>
) : ScrollbarAdapter {

    private val metrics = GridLineMetrics(gridState, lines)

    override val viewportSize: Double
        get() = gridState.layoutInfo.viewportSize.height.toDouble()

    override val contentSize: Double
        get() {
            metrics.refresh()
            return metrics.contentHeight()
        }

    override val scrollOffset: Double
        get() {
            metrics.refresh()
            val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.row != LazyGridItemInfo.UnknownRow }
                ?: return 0.0
            val (rowOffset, _) = metrics.locateRow(first.row)
            return (rowOffset - first.offset.y).toDouble()
        }

    override suspend fun scrollTo(scrollOffset: Double) {
        metrics.refresh()
        val distance = scrollOffset - this.scrollOffset
        // Mirrors Compose's own LazyLineContentAdapter: small moves scroll smoothly, large
        // moves (e.g. a drag jump) snap directly so we don't recompose every row in between.
        if (abs(distance) <= viewportSize) {
            gridState.scrollBy(distance.toFloat())
        } else {
            snapTo(scrollOffset)
        }
    }

    private suspend fun snapTo(scrollOffset: Double) {
        metrics.refresh()
        val maxOffset = (contentSize - viewportSize).coerceAtLeast(0.0)
        val target = scrollOffset.coerceIn(0.0, maxOffset).roundToInt().toLong()
        val (index, offsetWithin) = metrics.locate(target)
        gridState.scrollToItem(index, offsetWithin)
    }
}
