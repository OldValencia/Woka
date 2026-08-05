package com.woka.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Single-line text that slides smoothly back and forth (Apple-style) when
 * [animate] is true and the text is wider than its box, so long names stay
 * readable. When the text fits, or [animate] is false, it renders static.
 */
@Composable
fun Marquee(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    animate: Boolean = false
) {
    var textWidth by remember { mutableStateOf(0) }
    var boxWidth by remember { mutableStateOf(0) }
    val overflow = (textWidth - boxWidth).coerceAtLeast(0)
    val scroll = animate && overflow > 0

    val transition = rememberInfiniteTransition(label = "marquee")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (scroll) -overflow.toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = overflow * 16 + 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "marqueeOffset"
    )
    val x = if (scroll) animated else 0f

    Box(modifier = modifier.clipToBounds().onSizeChanged { boxWidth = it.width }) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            onTextLayout = { textWidth = it.size.width },
            // Measure at full width (ignore the box's max constraint) so overflow
            // is detected; the box clips and the offset reveals the rest.
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .offset { IntOffset(x.roundToInt(), 0) }
        )
    }
}
