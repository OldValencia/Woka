package com.woka.feature.viewer.shell.slider

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
import com.woka.core.ui.theme.Theme

private val PILL_GAP = 10.dp

/** Wide enough to read a scene from, narrow enough to sit over a control bar. */
private val PREVIEW_WIDTH = 168.dp

/**
 * The pill above the seek bar (§4). Shown on hover as well as on drag, and on
 * every file: the time alone when there is no chapter under the cursor, the
 * chapter title above it when there is.
 *
 * When previewAspect is non-null the same pill *grows upward* into a frame window
 * rather than a second floating panel appearing beside it — one object that gets
 * taller, which is why the image is flush to the pill's edges and only the text keeps
 * its padding. [animateContentSize] makes that growth a move rather than a jump, and
 * the position provider re-reads the height in the same pass, so the pill stays
 * anchored to the bar while it grows.
 */
@Composable
internal fun MediaSliderTooltip(
    x: Float,
    timeText: String,
    chapterTitle: String?,
    preview: SliderPreview? = null
) {
    val gapPx = with(LocalDensity.current) { PILL_GAP.roundToPx() }
    val position = remember(x, gapPx) { SliderTooltipPosition(x, gapPx) }
    val shape = RoundedCornerShape(Theme.viewer.tooltipCornerRadius)
    val aspect = preview?.aspect

    Popup(popupPositionProvider = position) {
        Column(
            modifier = Modifier
                .widthIn(min = if (aspect != null) PREVIEW_WIDTH else 0.dp, max = 240.dp)
                .clip(shape)
                .background(Theme.viewer.tooltipScrim)
                .border(1.dp, Theme.viewer.onScrimBorder, shape)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (aspect != null) {
                SliderPreviewWindow(preview.painter, aspect)
            }
            Column(
                modifier = Modifier.padding(Theme.viewer.tooltipPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (chapterTitle != null) {
                    BasicText(
                        text = chapterTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = Theme.viewer.onScrim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                // The pill exists to answer "where does this land?", so the time
                // is its headline and carries the larger size.
                BasicText(
                    text = timeText,
                    style = TextStyle(
                        color = if (chapterTitle != null) Theme.viewer.onScrimSecondary
                        else Theme.viewer.onScrim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
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
