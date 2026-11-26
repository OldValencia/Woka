package com.woka.feature.editor.engine.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.viewer.video.formatTime
import java.awt.Cursor

const val TIMELINE_RULER_TAG = "editor-timeline-ruler"

/** Ticks between two labels — the prototype's ruler draws one every 1/32 of eight labels. */
private const val TICKS_PER_STEP = 4

/**
 * The ruler (`.timeline .ruler`): time labels in the mono face over a row of ticks, on the lanes'
 * own geometry so a zoom and a scroll move them with the clips. **It is where the play head is
 * moved** (RD-301): a press seeks and a drag scrubs — a click on a clip only selects it now.
 *
 * The labels are drawn, not composed: a zoomed film scrolls them every frame it follows the
 * play head, and a composable per label would recompose the row each time.
 */
@Composable
internal fun TimelineRuler(state: StripState, clips: List<TrimClip>, geometry: StripGeometry) {
    val measurer = rememberTextMeasurer()
    val style = TimelineTokens.ruler.copy(color = Theme.room.faint)
    val tickColour = Theme.room.line
    val current by rememberUpdatedState(geometry)
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR)))
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    scrub(state, current, down)
                }
            }
            .testTag(TIMELINE_RULER_TAG)
    ) {
        val total = clips.totalMs()
        if (total <= 0L || geometry.pxPerMs <= 0f) return@Canvas
        val step = rulerStepMs(geometry.pxPerMs, TimelineTokens.rulerLabelSpacing.toPx())
        val tick = (step / TICKS_PER_STEP).coerceAtLeast(1L)
        val from = geometry.timelineAt(0f) / tick * tick
        val to = minOf(total, geometry.timelineAt(size.width) + tick)
        val tickHeight = TimelineTokens.tickHeight.toPx()
        var at = from
        while (at <= to) {
            val x = geometry.xOf(at)
            drawRect(tickColour, Offset(x, size.height - tickHeight), Size(1f, tickHeight))
            if (at % step == 0L) {
                val label = measurer.measure(rulerLabel(at, step), style)
                drawText(label, topLeft = Offset(x, size.height - tickHeight - 3f - label.size.height))
            }
            at += tick
        }
    }
}

/**
 * The smallest 1, 2 or 5 × 10ⁿ ms step that leaves [minPx] between two labels at [pxPerMs] —
 * a tenth of a second at the deepest zoom, minutes across a long film.
 */
internal fun rulerStepMs(pxPerMs: Float, minPx: Float): Long {
    var decade = 100L
    while (true) {
        for (factor in longArrayOf(1L, 2L, 5L)) {
            val step = decade * factor
            if (step * pxPerMs >= minPx || step >= 24L * 3_600_000L) return step
        }
        decade *= 10L
    }
}

/** `0:08`, or `0:08.5` while the step is under a second. */
private fun rulerLabel(ms: Long, step: Long): String =
    if (step >= 1000L) formatTime(ms) else "${formatTime(ms / 1000L * 1000L)}.${ms % 1000L / 100L}"
