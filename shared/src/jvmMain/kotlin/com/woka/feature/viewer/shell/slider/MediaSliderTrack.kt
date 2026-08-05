package com.woka.feature.viewer.shell.slider

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SEGMENT_GAP = 2.dp
private val HOVER_GROWTH = 2.dp

// Lightening applied over the inactive colour: the segment under the cursor, and
// the stretch the bar would jump to. Expressed as a blend towards white rather
// than an alpha, because the colour the caller passes in is opaque — dropping its
// alpha would darken it against the panel, not lighten it.
private const val HOVER_LIGHTEN = 0.30f
private const val PREVIEW_LIGHTEN = 0.50f

/**
 * One piece of the track, as fractions of the whole bar. Video chapters are the
 * only thing producing these today, but the slider does not know that: it used to
 * take `List<MediaChapter>` and call into the video package to interpret them,
 * which is what made the "shared" slider depend on the player.
 */
@Immutable
data class SliderSegment(val start: Float, val end: Float, val label: String? = null)

/** The whole bar as one piece — what a file with no chapters gets. */
internal val WHOLE_TRACK = listOf(SliderSegment(0f, 1f))

/**
 * The slider's track (§4): one rounded piece per segment, or a single full-width
 * piece when there are none — one drawing path, not two.
 *
 * At rest it is pixel-identical to the plain bar it replaces. Hovering the row
 * grows every segment, and the segment under the cursor grows once more and
 * lightens, which is the whole affordance: the gaps only need to be legible while
 * the pointer is on the bar.
 *
 * [previewProgress] is the YouTube-style hover preview — everything up to the
 * cursor drawn in a lighter shade, so the jump the click would make is visible
 * before it happens. Drawn under the played fill, which therefore still reads as
 * the real position.
 */
@Composable
internal fun MediaSliderTrack(
    progress: Float,
    previewProgress: Float?,
    segments: List<SliderSegment>,
    hoveredIndex: Int,
    hovered: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp
) {
    val baseHeight by animateDpAsState(if (hovered) trackHeight + HOVER_GROWTH else trackHeight)
    val tallHeight = baseHeight + HOVER_GROWTH
    val hoveredColor = lerp(inactiveColor, Color.White, HOVER_LIGHTEN)
    val previewColor = lerp(inactiveColor, Color.White, PREVIEW_LIGHTEN)

    Canvas(modifier = Modifier.fillMaxWidth().height(tallHeight)) {
        val gap = SEGMENT_GAP.toPx()
        val activeX = progress * size.width
        val previewX = previewProgress?.let { it * size.width }
        segments.forEachIndexed { index, segment ->
            val isHovered = index == hoveredIndex
            val barHeight = (if (isHovered) tallHeight else baseHeight).toPx()
            val top = (size.height - barHeight) / 2f
            // The gap is taken off the following segment's start, so the first
            // one still begins at 0 and the fill reads as one bar.
            val left = segment.start * size.width + if (index > 0) gap else 0f
            val right = segment.end * size.width
            if (right <= left) return@forEachIndexed
            val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
            drawRoundRect(
                color = if (isHovered) hoveredColor else inactiveColor,
                topLeft = Offset(left, top),
                size = Size(right - left, barHeight),
                cornerRadius = radius
            )
            val previewed = previewX?.coerceIn(left, right)
            if (previewed != null && previewed > left) {
                drawRoundRect(
                    color = previewColor,
                    topLeft = Offset(left, top),
                    size = Size(previewed - left, barHeight),
                    cornerRadius = radius
                )
            }
            val filled = activeX.coerceIn(left, right)
            if (filled > left) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(left, top),
                    size = Size(filled - left, barHeight),
                    cornerRadius = radius
                )
            }
        }
    }
}

/** Index of the segment under [x] pixels, or -1. Gaps belong to no segment. */
internal fun segmentIndexAt(segments: List<SliderSegment>, x: Float?, width: Float): Int {
    if (x == null || width <= 0f || segments.size < 2) return -1
    val fraction = (x / width).coerceIn(0f, 1f)
    return segments.indexOfFirst { fraction >= it.start && fraction < it.end }
}
