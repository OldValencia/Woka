package com.woka.core.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

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
 *
 * The arithmetic itself is in [KnownLayoutGridScrollbarAdapter].
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
