package com.woka.feature.folder

import com.woka.app.shell.sidebar.isWithin
import com.woka.app.shell.sidebar.samePath
import java.io.File
import kotlin.math.abs

/** An item on screen: its place in the folder's list, and where the grid drew it. */
data class ItemBox(val index: Int, val x: Int, val y: Int, val width: Int)

/**
 * Up or Down from [current] (SIDE-005): the item in the next row, nearest by x.
 *
 * Rows are told apart by their top edge, not by a column count: the grid mixes
 * folder cards spanning several cells with one-cell tiles, and a fixed stride would
 * walk from a folder into the middle of the photos. When the next row is not on
 * screen there is nothing to measure, so the move falls back to the current row's
 * length — the stride that is right everywhere except across a section's edge.
 */
fun stepVertically(current: Int, boxes: List<ItemBox>, down: Boolean, count: Int): Int {
    if (count == 0) return current
    val here = boxes.firstOrNull { it.index == current }
        ?: return (current + if (down) 1 else -1).coerceIn(0, count - 1)
    val beyond = boxes.filter { if (down) it.y > here.y else it.y < here.y }
    if (beyond.isEmpty()) {
        val rowLength = boxes.count { it.y == here.y }.coerceAtLeast(1)
        return (current + if (down) rowLength else -rowLength).coerceIn(0, count - 1)
    }
    val rowY = if (down) beyond.minOf { it.y } else beyond.maxOf { it.y }
    val centre = here.x + here.width / 2
    return beyond.filter { it.y == rowY }.minBy { abs(it.x + it.width / 2 - centre) }.index
}

/** Left or Right: the neighbour in the list, held at both ends. */
fun stepAcross(current: Int, right: Boolean, count: Int): Int =
    if (count == 0) current else (current + if (right) 1 else -1).coerceIn(0, count - 1)

/**
 * Where Backspace goes from [path]: its parent, while [path] lies inside a linked
 * root and is not that root. Null at a root, and for a folder opened from Explorer
 * outside every root — up from there is somewhere the library never went.
 */
fun parentWithinRoot(path: String, roots: List<String>): String? {
    val root = roots.firstOrNull { isWithin(it, path) } ?: return null
    if (samePath(root, path)) return null
    return File(path).absoluteFile.normalize().parentFile?.path
}
