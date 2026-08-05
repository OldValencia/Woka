package com.woka.core.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlin.math.ceil

/**
 * Row geometry for a grid described as [GridLine]s: how many rows each line
 * occupies, how tall they are, and where a given row or pixel offset falls.
 * Everything is in pixels from content start.
 *
 * Heights are *measured*, not assumed — filled in as rows of each kind scroll into
 * view at least once, which is why [refresh] must run before any query. A header
 * whose height is not yet known falls back to the cell height; the error that
 * leaves is bounded by one row and disappears the moment it has been on screen.
 *
 * Split from [KnownLayoutGridScrollbarAdapter] (§11), which is left implementing
 * the scrollbar contract on top of this.
 */
internal class GridLineMetrics(
    private val gridState: LazyGridState,
    private val lines: () -> List<GridLine>
) {
    private val headerHeights = HashMap<Any, Int>()
    private var cellHeight: Int = 0

    private val maxSpan: Int get() = gridState.layoutInfo.maxSpan.coerceAtLeast(1)
    private val lineSpacing: Int get() = gridState.layoutInfo.mainAxisItemSpacing

    fun refresh() {
        for (item in gridState.layoutInfo.visibleItemsInfo) {
            if (item.row == LazyGridItemInfo.UnknownRow) continue
            when (val line = lineAt(item.row)) {
                is GridLine.Header -> headerHeights[line.id] = item.size.height
                is GridLine.Cells -> cellHeight = item.size.height
            }
        }
    }

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

    /** Total height of all rows plus spacing and the grid's own content padding. */
    fun contentHeight(): Double {
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

    /** Pixel offset of the start of row [rowIndex], and that row's height. */
    fun locateRow(rowIndex: Int): Pair<Long, Int> {
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
    fun locate(targetOffset: Long): Pair<Int, Int> {
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
}
