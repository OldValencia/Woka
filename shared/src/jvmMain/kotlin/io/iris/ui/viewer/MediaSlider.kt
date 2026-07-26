package io.iris.ui.viewer

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

/**
 * A slim, YouTube-style horizontal slider: a thin track with a small dot that
 * grows while being dragged. The tap/drag hit area is kept generously tall
 * ([hitAreaHeight]) even though the visuals are compact.
 */
@Composable
fun MediaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    tooltipText: ((Float) -> String)? = null,
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
    thumbColor: Color = Color.White,
    trackHeight: Dp = 3.dp,
    thumbSize: Dp = 8.dp,
    thumbSizeDragged: Dp = 15.dp,
    hitAreaHeight: Dp = 24.dp
) {
    var dragX by remember { mutableStateOf<Float?>(null) }
    var width by remember { mutableStateOf(0f) }
    val isDragging = dragX != null

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val safeValue = value.coerceIn(0f, 1f)
    val displayValue = if (width > 0f) {
        dragX?.let { (it / width).coerceIn(0f, 1f) } ?: safeValue
    } else safeValue

    val animatedThumbSize by animateDpAsState(if (isDragging) thumbSizeDragged else thumbSize)

    Box(
        modifier = modifier
            .height(hitAreaHeight)
            .onSizeChanged { width = it.width.toFloat() }
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
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(inactiveColor)
        )
        // Active Track
        Box(
            modifier = Modifier
                .fillMaxWidth(displayValue)
                .height(trackHeight)
                .clip(CircleShape)
                .background(activeColor)
        )
        // Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset((displayValue * width - animatedThumbSize.toPx() / 2f).toInt(), 0) }
                .size(animatedThumbSize)
                .shadow(2.dp, CircleShape)
                .background(thumbColor, CircleShape)
        )

        if (tooltipText != null && dragX != null) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset((displayValue * width).toInt() - (width / 2f).toInt(), -35)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tooltipText(displayValue),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun MediaVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.2f),
) {
    var dragY by remember { mutableStateOf<Float?>(null) }
    var height by remember { mutableStateOf(0f) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)

    val safeValue = value.coerceIn(0f, 1f)
    val displayValue = if (height > 0f) {
        dragY?.let { (1f - (it / height)).coerceIn(0f, 1f) } ?: safeValue
    } else safeValue

    Box(
        modifier = modifier
            .onSizeChanged { height = it.height.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragY = offset.y
                        currentOnValueChange((1f - (offset.y / height)).coerceIn(0f, 1f))
                    },
                    onDragEnd = { dragY = null },
                    onDragCancel = { dragY = null }
                ) { change, dragAmount ->
                    change.consume()
                    dragY = (dragY ?: 0f) + dragAmount.y
                    currentOnValueChange((1f - (dragY!! / height)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnValueChange((1f - (offset.y / height)).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(inactiveColor)
        )
        // Active Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(displayValue)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
}
