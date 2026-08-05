package com.woka.feature.viewer.shell.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider

private val PILL_GAP = 10.dp

/**
 * The pill above the seek bar (§4). Shown on hover as well as on drag, and on
 * every file: the time alone when there is no chapter under the cursor, the
 * chapter title above it when there is.
 */
@Composable
internal fun MediaSliderTooltip(x: Float, timeText: String, chapterTitle: String?) {
    val gapPx = with(LocalDensity.current) { PILL_GAP.roundToPx() }
    val position = remember(x, gapPx) { SliderTooltipPosition(x, gapPx) }

    Popup(popupPositionProvider = position) {
        Column(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (chapterTitle != null) {
                Text(
                    text = chapterTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = timeText,
                color = Color.White.copy(alpha = if (chapterTitle != null) 0.7f else 1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Places the pill fully **above** the bar, centred on the cursor and held inside
 * the track at both ends.
 *
 * A position provider rather than `Popup(alignment, offset)` driven by a measured
 * size: the popup's own height arrives here in the same layout pass. A remembered
 * size is zero on the frame the pill appears — and this pill is removed from
 * composition every time the pointer leaves, so that frame is *every* frame. A
 * zero height put the pill on top of the bar, where it took the pointer, and the
 * Enter/Exit oscillation that followed destroyed and recreated it forever. That
 * is why the pill never showed on hover while it worked during a drag.
 */
private class SliderTooltipPosition(
    private val anchorX: Float,
    private val gapPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val half = popupContentSize.width / 2
        val minCentre = anchorBounds.left + half
        val maxCentre = (anchorBounds.right - half).coerceAtLeast(minCentre)
        val centre = (anchorBounds.left + anchorX).toInt().coerceIn(minCentre, maxCentre)
        val maxLeft = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        return IntOffset(
            x = (centre - half).coerceIn(0, maxLeft),
            y = anchorBounds.top - popupContentSize.height - gapPx
        )
    }
}
