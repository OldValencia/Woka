package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.ui.theme.Theme
import kotlin.math.roundToInt

/** A minimal horizontal slider mapping the track position to a zoom percentage. */
@Composable
fun ZoomSliderView(percent: Int, onPercent: (Int) -> Unit, modifier: Modifier = Modifier) {
    val min = SizeMode.MIN_PERCENT
    val max = SizeMode.MAX_PERCENT
    val thumb = 14.dp

    BoxWithConstraints(modifier.width(150.dp).height(24.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbPx = with(LocalDensity.current) { thumb.toPx() }
        val fraction = ((percent - min).toFloat() / (max - min)).coerceIn(0f, 1f)
        val travel = (widthPx - thumbPx).coerceAtLeast(1f)

        fun applyFromX(x: Float) {
            val f = (x / widthPx).coerceIn(0f, 1f)
            onPercent(SizeMode.clampPercent((min + f * (max - min)).roundToInt()))
        }

        val gestures = Modifier
            .pointerInput(Unit) { detectTapGestures { applyFromX(it.x) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> applyFromX(change.position.x) } }

        Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center).clip(RoundedCornerShape(2.dp)).background(Theme.colors.border))
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(2.dp)).background(Theme.colors.textPrimary))
        Box(
            Modifier
                .offset { IntOffset((fraction * travel).roundToInt(), 0) }
                .size(thumb)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(Theme.colors.textPrimary)
        )
        Box(Modifier.fillMaxWidth().height(24.dp).then(gestures))
    }
}
