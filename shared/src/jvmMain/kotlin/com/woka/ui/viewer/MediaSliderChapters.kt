package com.woka.ui.viewer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.ui.viewer.player.MediaChapter

private val SEGMENT_GAP = 2.dp
private val HOVER_GROWTH = 2.dp

// Lightening applied over [inactiveColor]: the segment under the cursor, and the
// stretch the bar would jump to. Expressed as a blend towards white rather than
// an alpha, because the colour the player passes in is opaque — dropping its
// alpha would darken it against the panel, not lighten it.
private const val HOVER_LIGHTEN = 0.30f
private const val PREVIEW_LIGHTEN = 0.50f

/**
 * The slider's track (§4): one rounded segment per container chapter, or a
 * single full-width piece when the file has none — one drawing path, not two.
 *
 * At rest it is pixel-identical to the plain bar it replaces. Hovering the row
 * grows every segment, and the segment under the cursor grows once more and
 * lightens, which is the whole affordance: the gaps only need to be legible
 * while the pointer is on the bar.
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
    chapters: List<MediaChapter>,
    durationMs: Long,
    hoveredIndex: Int,
    hovered: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp
) {
    val baseHeight by animateDpAsState(if (hovered) trackHeight + HOVER_GROWTH else trackHeight)
    val tallHeight = baseHeight + HOVER_GROWTH
    val spans = chapterSpans(chapters, durationMs)
    val hoveredColor = lerp(inactiveColor, Color.White, HOVER_LIGHTEN)
    val previewColor = lerp(inactiveColor, Color.White, PREVIEW_LIGHTEN)

    Canvas(modifier = Modifier.fillMaxWidth().height(tallHeight)) {
        val gap = SEGMENT_GAP.toPx()
        val activeX = progress * size.width
        val previewX = previewProgress?.let { it * size.width }
        spans.forEachIndexed { index, span ->
            val isHovered = index == hoveredIndex
            val barHeight = (if (isHovered) tallHeight else baseHeight).toPx()
            val top = (size.height - barHeight) / 2f
            // The gap is taken off the following segment's start, so the first
            // one still begins at 0 and the fill reads as one bar.
            val left = span.first * size.width + if (index > 0) gap else 0f
            val right = span.second * size.width
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

/** Fractional `start..end` of each segment, or the whole bar with no chapters. */
private fun chapterSpans(
    chapters: List<MediaChapter>,
    durationMs: Long
): List<Pair<Float, Float>> {
    if (chapters.isEmpty() || durationMs <= 0L) return listOf(0f to 1f)
    return chapters.map { chapter ->
        (chapter.startMs.toFloat() / durationMs) to (chapter.endMs.toFloat() / durationMs)
    }
}

/** Index of the chapter under [x] pixels, or -1. Gaps belong to no chapter. */
internal fun chapterIndexAt(
    chapters: List<MediaChapter>,
    durationMs: Long,
    x: Float?,
    width: Float
): Int {
    if (x == null || width <= 0f || durationMs <= 0L || chapters.isEmpty()) return -1
    val positionMs = ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
    return chapters.indexOfFirst { positionMs >= it.startMs && positionMs < it.endMs }
}
