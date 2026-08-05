package com.woka.feature.viewer.shell.slider

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * A slim, YouTube-style horizontal slider: a thin track with a small dot that
 * grows while being dragged. The tap/drag hit area is kept generously tall
 * (`style.hitAreaHeight`) even though the visuals are compact.
 *
 * With more than one [SliderSegment] the track is drawn segmented (§4);
 * segmentation is purely visual — a click still seeks to the clicked position,
 * never to a boundary. Segments arrive already expressed as fractions, so this
 * genuinely is a shared slider: it no longer calls into the video package to work
 * out what a chapter is.
 */
@Composable
fun MediaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    style: MediaSliderStyle,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    tooltipText: ((Float) -> String)? = null,
    segments: List<SliderSegment> = WHOLE_TRACK
) {
    var dragX by remember { mutableStateOf<Float?>(null) }
    var hoverX by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(0f) }
    val isDragging = dragX != null

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val safeValue = value.coerceIn(0f, 1f)
    val displayValue = if (width > 0f) {
        dragX?.let { (it / width).coerceIn(0f, 1f) } ?: safeValue
    } else safeValue

    // The pointer moves at frame rate, so the segment under it is derived rather
    // than recomposing the whole control bar on every Move event.
    val hoveredIndex by remember(segments) {
        derivedStateOf { segmentIndexAt(segments, hoverX ?: dragX, width) }
    }

    // Where the bar would jump to, shown as a lighter fill up to the cursor. Not
    // during a drag: the accent fill is already following the pointer there.
    val previewProgress = hoverX
        ?.takeIf { !isDragging && width > 0f }
        ?.let { (it / width).coerceIn(0f, 1f) }

    val animatedThumbSize by animateDpAsState(
        if (isDragging) style.thumbSizeDragged else style.thumbSize
    )

    Box(
        modifier = modifier
            .height(style.hitAreaHeight)
            .onSizeChanged { width = it.width.toFloat() }
            .sliderHover { hoverX = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragX = offset.x
                        currentOnValueChange((offset.x / width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        dragX = null
                        currentOnValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        dragX = null
                        currentOnValueChangeFinished?.invoke()
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragX = (dragX ?: 0f) + dragAmount.x
                    currentOnValueChange((dragX!! / width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newVal = (offset.x / width).coerceIn(0f, 1f)
                    currentOnValueChange(newVal)
                    currentOnValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        MediaSliderTrack(
            progress = displayValue,
            previewProgress = previewProgress,
            segments = segments,
            hoveredIndex = hoveredIndex,
            hovered = hoverX != null || isDragging,
            activeColor = style.activeColor,
            inactiveColor = style.inactiveColor,
            trackHeight = style.trackHeight
        )
        // Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset((displayValue * width - animatedThumbSize.toPx() / 2f).toInt(), 0) }
                .size(animatedThumbSize)
                .shadow(2.dp, CircleShape)
                .background(style.thumbColor, CircleShape)
        )

        // Follows the cursor, and falls back to the drag once the pointer has
        // left the row mid-scrub.
        val pillX = hoverX ?: dragX
        if (tooltipText != null && pillX != null && width > 0f) {
            MediaSliderTooltip(
                x = pillX.coerceIn(0f, width),
                timeText = tooltipText((pillX / width).coerceIn(0f, 1f)),
                chapterTitle = segments.getOrNull(hoveredIndex)?.label
            )
        }
    }
}
