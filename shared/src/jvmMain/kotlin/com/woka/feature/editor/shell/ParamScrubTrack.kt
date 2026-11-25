package com.woka.feature.editor.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.FloatParam
import kotlin.math.abs

/**
 * The hairline along a scrub row's foot (RD-202, `.scrub-fill`, `.scrub-thumb`, `.detent`): a
 * fill from the default to the value, a small thumb at the value, and the default's tick.
 * Orange once the value has moved, grey while it sits at the default; taller while dragged.
 */
@Composable
internal fun ScrubTrack(spec: FloatParam, value: Float, changed: Boolean, active: Boolean, dragging: Boolean, modifier: Modifier) {
    val room = Theme.room
    val span = spec.range.endInclusive - spec.range.start
    fun at(v: Float) = if (span == 0f) 0f else ((v - spec.range.start) / span).coerceIn(0f, 1f)
    val snapped = dragging && !changed
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val bottom = size.height
        if (active) drawRect(room.line, Offset(0f, bottom - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
        val from = at(spec.detent) * size.width
        val to = at(value) * size.width
        val fill = (if (dragging) 3.dp else 2.dp).toPx()
        drawRect(if (changed) room.accent else room.faint, Offset(minOf(from, to), bottom - fill), Size(abs(to - from), fill))
        val tick = (if (snapped) 8.dp else 5.dp).toPx()
        drawRect(if (snapped) room.accent else room.faint, Offset(from, bottom - tick), Size(1.dp.toPx(), tick))
        if (changed || active) {
            val thumb = (if (dragging) 12.dp else 8.dp).toPx()
            val half = 2.dp.toPx()
            drawRect(if (changed) room.accent else room.muted, Offset(to - half, bottom - thumb), Size(half * 2, thumb))
        }
    }
}
