package com.woka.core.ui.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * One row of a vertically scrolling LazyVerticalGrid: either a single full-span "header" row, or
 * a block of itemCount uniform square item rows. The list passed to
 * [rememberKnownLayoutScrollbarAdapter] must describe the item()/items() calls emitted into the
 * grid, in the same order, or the size computed below won't match the real layout.
 */
sealed class GridLine {
    data class Header(val id: Any) : GridLine()
    data class Cells(val itemCount: Int) : GridLine()
}

/**
 * A [ScrollbarAdapter] for grids built from [GridLine]s, computing scroll offset and content
 * size exactly from the known row layout instead of Compose's built-in scrollbar heuristic
 * (which averages the height of whatever rows are currently visible).
 *
 * That average is fine for uniform grids, but rows of very different heights (e.g. a full-span
 * text section header mixed with square image cells) throw it off enough that a fast scrollbar
 * drag can get stuck short of the real end: the thumb hits the *estimated* end of the track
 * while the grid can still really scroll further (see
 * https://github.com/JetBrains/compose-multiplatform/issues/2679). Since the caller already
 * knows exactly how many rows of each kind exist, we compute the real size instead of guessing.
 */
@Composable
fun rememberKnownLayoutScrollbarAdapter(
    gridState: LazyGridState,
    lines: () -> List<GridLine>
): ScrollbarAdapter {
    val currentLines = rememberUpdatedState(lines)
    return remember(gridState) {
        KnownLayoutGridScrollbarAdapter(gridState) { currentLines.value() }
    }
}

private class KnownLayoutGridScrollbarAdapter(
    private val gridState: LazyGridState,
    private val lines: () -> List<GridLine>
) : ScrollbarAdapter {

    // Real, measured heights, filled in as rows of each kind become visible at least once.
    private val headerHeights = HashMap<Any, Int>()
    private var cellHeight: Int = 0

    private val maxSpan: Int get() = gridState.layoutInfo.maxSpan.coerceAtLeast(1)
    private val lineSpacing: Int get() = gridState.layoutInfo.mainAxisItemSpacing

    private fun rowCountOf(line: GridLine): Int = when (line) {
        is GridLine.Header -> 1
        is GridLine.Cells -> if (line.itemCount == 0) 0 else ceil(line.itemCount.toDouble() / maxSpan).toInt()
    }

    private fun heightOf(line: GridLine): Int = when (line) {
        is GridLine.Header -> headerHeights[line.id] ?: cellHeight
        is GridLine.Cells -> cellHeight
    }

    private fun lineAt(rowIndex: Int): GridLine {
        var remaining = rowIndex
        for (line in lines()) {
            val rowCount = rowCountOf(line)
            if (remaining < rowCount) return line
            remaining -= rowCount
        }
        return GridLine.Cells(0)
    }

    private fun refreshMeasuredHeights() {
        for (item in gridState.layoutInfo.visibleItemsInfo) {
            if (item.row == LazyGridItemInfo.UnknownRow) continue
            when (val line = lineAt(item.row)) {
                is GridLine.Header -> headerHeights[line.id] = item.size.height
                is GridLine.Cells -> cellHeight = item.size.height
            }
        }
    }

    /** Pixel offset (from content start) of the start of row [rowIndex], and that row's height. */
    private fun locateRow(rowIndex: Int): Pair<Long, Int> {
        var offset = 0L
        var remaining = rowIndex
        for (line in lines()) {
            val rowCount = rowCountOf(line)
            if (rowCount == 0) continue
            val height = heightOf(line)
            val rowStride = height + lineSpacing
            if (remaining < rowCount) {
                return (offset + remaining.toLong() * rowStride) to height
            }
            offset += rowCount.toLong() * rowStride
            remaining -= rowCount
        }
        return offset to cellHeight
    }

    /** Given a pixel offset from content start, the item index and in-item offset to scroll to. */
    private fun locate(targetOffset: Long): Pair<Int, Int> {
        var offset = 0L
        var itemIndex = 0
        val allLines = lines()
        allLines.forEachIndexed { i, line ->
            val rowCount = rowCountOf(line)
            if (rowCount == 0) return@forEachIndexed
            val height = heightOf(line)
            val rowStride = height + lineSpacing
            val blockSize = rowCount.toLong() * rowStride
            val isLastLine = i == allLines.lastIndex
            if (targetOffset < offset + blockSize || isLastLine) {
                val withinBlock = (targetOffset - offset).coerceAtLeast(0)
                val rowInBlock = (withinBlock / rowStride).toInt().coerceIn(0, rowCount - 1)
                val rowItemIndex = itemIndex + if (line is GridLine.Cells) rowInBlock * maxSpan else 0
                val rowOffset = offset + rowInBlock.toLong() * rowStride
                return rowItemIndex to (targetOffset - rowOffset).toInt().coerceAtLeast(0)
            }
            offset += blockSize
            itemIndex += when (line) {
                is GridLine.Header -> 1
                is GridLine.Cells -> line.itemCount
            }
        }
        return 0 to 0
    }

    override val viewportSize: Double
        get() = gridState.layoutInfo.viewportSize.height.toDouble()

    override val contentSize: Double
        get() {
            refreshMeasuredHeights()
            val contentPadding = gridState.layoutInfo.beforeContentPadding + gridState.layoutInfo.afterContentPadding
            var sum = 0L
            var totalRows = 0
            for (line in lines()) {
                val rowCount = rowCountOf(line)
                if (rowCount == 0) continue
                sum += rowCount.toLong() * heightOf(line)
                totalRows += rowCount
            }
            sum += lineSpacing.toLong() * (totalRows - 1).coerceAtLeast(0)
            sum += contentPadding
            return sum.toDouble()
        }

    override val scrollOffset: Double
        get() {
            refreshMeasuredHeights()
            val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.row != LazyGridItemInfo.UnknownRow }
                ?: return 0.0
            val (rowOffset, _) = locateRow(first.row)
            return (rowOffset - first.offset.y).toDouble()
        }

    override suspend fun scrollTo(scrollOffset: Double) {
        refreshMeasuredHeights()
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
        refreshMeasuredHeights()
        val maxOffset = (contentSize - viewportSize).coerceAtLeast(0.0)
        val target = scrollOffset.coerceIn(0.0, maxOffset).roundToInt().toLong()
        val (index, offsetWithin) = locate(target)
        gridState.scrollToItem(index, offsetWithin)
    }
}
