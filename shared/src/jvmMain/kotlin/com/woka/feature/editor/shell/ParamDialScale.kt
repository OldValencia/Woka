package com.woka.feature.editor.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.FloatParam
import kotlin.math.roundToInt

/** One degree along the scale (`.dial-scale`, `DIAL_STEP` in `instruments.js`). */
internal val DIAL_DEGREE = 8.dp

/** The window the scale moves in (`.dial-window`). */
private val WINDOW_HEIGHT = 36.dp

/** A degree's tick and a fifth degree's (`.dial-scale` background sizes 9 px and 15 px). */
private val MINOR_TICK = 9.dp
private val MAJOR_TICK = 15.dp
private const val MAJOR_EVERY = 5
private const val LABEL_EVERY = 15

/** The fixed needle (`.dial-needle`): 2 × 21 px from the window's top. */
private val NEEDLE_WIDTH = 2.dp
private val NEEDLE_HEIGHT = 21.dp

/** Where the window is fully opaque (`.dial-window` mask, 24 %–76 %). */
private const val FADE_IN = 0.24f
private const val FADE_OUT = 0.76f

/**
 * The dial's scale and needle (RD-205, `.dial-window`): ticks every degree, longer every
 * fifth, numbers every fifteenth, sliding under a needle fixed at the centre, and fading out
 * at both ends so the scale reads as a wheel rather than a ruler.
 *
 * [shown] is the value the scale stands at, which glides to a written value and follows a
 * drag exactly — the caller animates it. [needle] scales the needle's height, the pulse on
 * arriving at zero. Split from `ParamDial` along the drawing/input seam.
 */
@Composable
internal fun DialScale(spec: FloatParam, shown: Float, needle: Float, modifier: Modifier = Modifier) {
    val room = Theme.room
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(room.muted) {
        TextStyle(color = room.muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
    val from = spec.range.start.roundToInt()
    val to = spec.range.endInclusive.roundToInt()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(WINDOW_HEIGHT)
            // Offscreen, so the fade below masks what is already drawn rather than the well.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val degree = DIAL_DEGREE.toPx()
        val centre = size.width / 2f
        for (deg in from..to) {
            val x = centre + (deg - shown) * degree
            if (x < -degree || x > size.width + degree) continue
            val major = deg % MAJOR_EVERY == 0
            val tick = if (major) MAJOR_TICK.toPx() else MINOR_TICK.toPx()
            drawRect(if (major) room.muted else room.faint, Offset(x, 0f), Size(1.dp.toPx(), tick))
            if (deg % LABEL_EVERY == 0) {
                val label = measurer.measure(deg.toString(), labelStyle)
                val at = Offset(x - label.size.width / 2f, size.height - label.size.height - 1.dp.toPx())
                drawText(label, topLeft = at)
            }
        }
        drawNeedle(room.accent, needle)
        drawFade()
    }
}

private fun DrawScope.drawNeedle(color: Color, stretch: Float) {
    val width = NEEDLE_WIDTH.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width / 2f - width / 2f, 0f),
        size = Size(width, NEEDLE_HEIGHT.toPx() * stretch),
        cornerRadius = CornerRadius(width / 2f)
    )
}

/** Keeps what lies under the opaque middle and lets both ends go to nothing. */
private fun DrawScope.drawFade() {
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            FADE_IN to Color.Black,
            FADE_OUT to Color.Black,
            1f to Color.Transparent
        ),
        blendMode = BlendMode.DstIn
    )
}
