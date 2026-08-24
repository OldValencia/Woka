package com.woka.core.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.data.ViewLayout

/**
 * Grid density: the ladder, what each rung is worth, and what each rung is called.
 *
 * [ViewLayout] already had exactly three steps, so density is not a second setting —
 * it is a second and third way to reach the one the Filter menu already changes.
 * Everything that moves the grid (Ctrl+wheel, the View submenu) walks this ladder,
 * so no two of them can disagree about what the next step is.
 */

/** How wide a grid column is at each density — the one place the three numbers live. */
val ViewLayout.gridColumnSize: Dp
    get() = when (this) {
        ViewLayout.SmallGrid -> 120.dp
        ViewLayout.Grid -> 240.dp
        ViewLayout.BigGrid -> 400.dp
    }

/** One step bigger, and one step smaller. Both ends of the ladder are stops. */
fun ViewLayout.zoomedIn(): ViewLayout = when (this) {
    ViewLayout.SmallGrid -> ViewLayout.Grid
    ViewLayout.Grid -> ViewLayout.BigGrid
    ViewLayout.BigGrid -> ViewLayout.BigGrid
}

fun ViewLayout.zoomedOut(): ViewLayout = when (this) {
    ViewLayout.BigGrid -> ViewLayout.Grid
    ViewLayout.Grid -> ViewLayout.SmallGrid
    ViewLayout.SmallGrid -> ViewLayout.SmallGrid
}

val ViewLayout.canZoomIn: Boolean get() = this != ViewLayout.BigGrid
val ViewLayout.canZoomOut: Boolean get() = this != ViewLayout.SmallGrid

/** The letter the readout shows. */
val ViewLayout.densityLabel: String
    get() = when (this) {
        ViewLayout.SmallGrid -> "S"
        ViewLayout.Grid -> "M"
        ViewLayout.BigGrid -> "L"
    }

/** The word under it, because a letter alone is not a name. */
val ViewLayout.densityName: String
    get() = when (this) {
        ViewLayout.SmallGrid -> "Small"
        ViewLayout.Grid -> "Medium"
        ViewLayout.BigGrid -> "Large"
    }
