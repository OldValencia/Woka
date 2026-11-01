package com.woka.app.shell.nowplaying

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/** `.ring`: the track, then the played part from twelve o'clock, clockwise. */
@Composable
internal fun ProgressRing(progress: Float, modifier: Modifier) {
    val track = Theme.colors.border
    val played = Theme.colors.accent
    Canvas(modifier) {
        val stroke = NowPlayingTokens.RingStroke.toPx()
        val inset = stroke / 2
        val arc = Size(size.width - stroke, size.height - stroke)
        drawCircle(track, radius = arc.width / 2, style = Stroke(stroke))
        drawArc(
            color = played,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

/** Three bars that rise and fall out of step: the at-rest sign that something is playing. */
@Composable
internal fun PlayingBars(color: Color) {
    val motion = rememberInfiniteTransition()
    val periods = listOf(900, 1100, 800)
    val heights = periods.map { period ->
        val height by motion.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(period / 2), RepeatMode.Reverse)
        )
        height
    }
    Canvas(Modifier.size(14.dp)) {
        val bar = size.width / 7
        heights.forEachIndexed { index, fraction ->
            val tall = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(bar * (1 + index * 2.5f), size.height - tall),
                size = Size(bar, tall),
                cornerRadius = CornerRadius(bar / 2)
            )
        }
    }
}

/** `.slot`: the four places a dragged bubble can land, the [nearest] in the accent. */
@Composable
internal fun CornerTargets(corners: List<IntOffset>, nearest: Int, bubblePx: Int) {
    val idle = Theme.colors.border
    val active = Theme.colors.accent
    Canvas(Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
        val radius = bubblePx / 2f
        corners.forEachIndexed { index, corner ->
            drawCircle(
                color = if (index == nearest) active else idle,
                radius = radius - 1f,
                center = Offset(corner.x + radius, corner.y + radius),
                style = Stroke(1.5.dp.toPx(), pathEffect = dash)
            )
        }
    }
}
