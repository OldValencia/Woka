package com.woka.feature.viewer.shell.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged

/**
 * Vertical counterpart of [MediaSlider], used only by the volume overlay. Split
 * out of `MediaSlider.kt` (§2 file-size mandate) — the two share nothing but a
 * visual language.
 */
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
