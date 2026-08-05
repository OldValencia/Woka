package com.woka.feature.viewer.pip

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PipCornerFlash() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )

    var showCorners by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3000.milliseconds)
        showCorners = false
    }

    if (showCorners) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val length = 32.dp.toPx()
            val padding = 16.dp.toPx()
            val w = size.width
            val h = size.height
            val color = Color.White.copy(alpha = alpha)

            // Top-Left
            drawLine(
                color,
                start = Offset(padding, padding),
                end = Offset(padding + length, padding),
                strokeWidth = strokeWidth
            )
            drawLine(
                color,
                start = Offset(padding, padding),
                end = Offset(padding, padding + length),
                strokeWidth = strokeWidth
            )

            // Top-Right
            drawLine(
                color,
                start = Offset(w - padding, padding),
                end = Offset(w - padding - length, padding),
                strokeWidth = strokeWidth
            )
            drawLine(
                color,
                start = Offset(w - padding, padding),
                end = Offset(w - padding, padding + length),
                strokeWidth = strokeWidth
            )

            // Bottom-Left
            drawLine(
                color,
                start = Offset(padding, h - padding),
                end = Offset(padding + length, h - padding),
                strokeWidth = strokeWidth
            )
            drawLine(
                color,
                start = Offset(padding, h - padding),
                end = Offset(padding, h - padding - length),
                strokeWidth = strokeWidth
            )

            // Bottom-Right
            drawLine(
                color,
                start = Offset(w - padding, h - padding),
                end = Offset(w - padding - length, h - padding),
                strokeWidth = strokeWidth
            )
            drawLine(
                color,
                start = Offset(w - padding, h - padding),
                end = Offset(w - padding, h - padding - length),
                strokeWidth = strokeWidth
            )
        }
    }
}
