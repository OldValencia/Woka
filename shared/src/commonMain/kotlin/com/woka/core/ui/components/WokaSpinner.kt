package com.woka.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The indeterminate spinner: a ring of tapered spokes, one bright and the rest
 * fading behind it, stepping round in discrete jumps.
 *
 * Replaces `material3.CircularProgressIndicator`, which was the last reason two
 * files held the dependency. It is not a like-for-like copy and cannot be: the
 * Material one sweeps a continuous arc in `colorScheme.primary`, and since this app
 * never provides a `MaterialTheme` that resolved to the **default Material purple**
 * `#6650A4` — a colour that appears nowhere else in Woka and was never chosen. The
 * spoke wheel is the HIG form, and the colour is now a parameter with no default
 * worth inheriting.
 *
 * Discrete steps rather than a smooth rotation, because that is what the form is:
 * a continuously spun spoke wheel reads as a blur, while the jump from spoke to
 * spoke is what the eye picks up as "working".
 */
@Composable
fun WokaSpinner(
    modifier: Modifier = Modifier,
    diameter: Dp = 26.dp,
    color: Color = Color.White,
    strokeWidth: Dp = 2.dp,
    spokes: Int = 8
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    // Runs 0 → spokes and restarts, so `floor` gives the index of the leading
    // spoke. One full turn per `spokes * 100` ms — 800 ms at eight, which is the
    // pace a HIG wheel turns at.
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = spokes.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = spokes * 100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "step"
    )

    Canvas(modifier = modifier.size(diameter)) {
        val head = floor(step).toInt()
        val stroke = strokeWidth.toPx()
        // Inset by half the stroke so a round cap lands inside the bounds rather
        // than being clipped by them.
        val outer = size.minDimension / 2f - stroke / 2f
        val inner = outer * 0.52f

        repeat(spokes) { index ->
            // How far behind the leading spoke this one is. Fading *backwards* from
            // the head is what gives the wheel a direction of travel; fade it
            // symmetrically and it only pulses.
            val behind = (head - index + spokes) % spokes
            val fade = 1f - behind.toFloat() / spokes * 0.82f
            val angle = index.toFloat() / spokes * 2f * PI.toFloat() - PI.toFloat() / 2f
            val dx = cos(angle)
            val dy = sin(angle)
            drawLine(
                // Multiplied, not replaced: a caller passing a translucent colour
                // means it, and `copy(alpha = fade)` would quietly discard that.
                color = color.copy(alpha = color.alpha * fade),
                start = Offset(center.x + dx * inner, center.y + dy * inner),
                end = Offset(center.x + dx * outer, center.y + dy * outer),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}
