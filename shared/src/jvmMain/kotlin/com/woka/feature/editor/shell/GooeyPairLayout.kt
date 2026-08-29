package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt

/**
 * Where the two cards sit, worked out **in the frame it is drawn in**.
 *
 * The first version laid them out in a `Row` with a spacer whose width came from
 * `onPlaced`, which arrives a frame late — so the card lurched left as the panel took its
 * width, then right as the spacer caught up, and only then did anything slide (reported by
 * hand 2026-08-29). Here the panel's width is known during measurement, so one number
 * decides everything and there is nothing to catch up with.
 *
 * The panel travels from **entirely inside the card's own footprint** to a gap beyond its
 * right edge. The pair grows by exactly that travel, so the card — centred by the panel
 * above — glides left by half of it: it moves, smoothly, once, and never comes back.
 */
@Composable
internal fun PairLayout(
    progress: Float,
    gap: Float,
    main: @Composable () -> Unit,
    extra: @Composable () -> Unit
) {
    Layout(contents = listOf(main, extra)) { (mainOnly, extraOnly), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val card = mainOnly.first().measure(loose)
        val panel = extraOnly.firstOrNull()?.measure(loose)
        val travel = if (panel == null) 0f else (panel.width + gap) * progress
        val width = card.width + travel.roundToInt()
        val height = maxOf(card.height, panel?.height ?: 0)

        layout(width, height) {
            // Bottom-aligned: both float above the dock and are read off that edge.
            card.place(0, height - card.height)
            if (panel != null) {
                // From `card.width - panel.width` — tucked wholly behind the card, where
                // the two plates are one blob — to `card.width + gap`.
                val from = (card.width - panel.width).toFloat()
                val to = card.width + gap
                panel.place((from + (to - from) * progress).roundToInt(), height - panel.height)
            }
        }
    }
}

/** Where one card sits inside the pair, in the pixels the blob is drawn in. */
internal data class PlateBounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)

/** A child's own rectangle, in the coordinates the blob behind it is drawn in. */
internal fun LayoutCoordinates.bounds(): PlateBounds {
    val at = positionInParent()
    return PlateBounds(at.x, at.y, size.width.toFloat(), size.height.toFloat())
}
