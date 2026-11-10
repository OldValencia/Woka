package com.woka.feature.editor.engine.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.TimeWindow
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.totalMs
import kotlin.math.roundToInt

const val MARKED_SPAN_TAG = "editor_marked_span"

private val SPAN_HEIGHT = 3.dp

/**
 * Where the selected shape is on screen, as a thin accent bar along the strip's top (ME-263,
 * mockup state 7) — time is the strip's to show. Clamped to the result, so a window reaching
 * past the end stops at it.
 */
@Composable
internal fun MarkedSpan(geometry: StripGeometry, clips: List<TrimClip>, window: TimeWindow) {
    val last = clips.totalMs() - 1L
    if (last < 0L || window.atMs > last) return
    val left = geometry.xOf(window.atMs)
    val right = geometry.xOf(minOf(window.endMs, last))
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), 0) }
            .width(with(density) { (right - left).coerceAtLeast(1f).toDp() })
            .height(SPAN_HEIGHT)
            .clip(RoundedCornerShape(SPAN_HEIGHT / 2))
            .background(Theme.colors.accentEditor)
            .testTag(MARKED_SPAN_TAG)
    )
}
