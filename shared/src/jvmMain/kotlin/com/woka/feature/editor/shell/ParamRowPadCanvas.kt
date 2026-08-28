package com.woka.feature.editor.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamPoint
import com.woka.feature.editor.core.ParamSet

/**
 * The pad itself, at the size a person can aim in — drawn inside the popover its
 * indicator opens (ME-141, moved off the panel 2026-08-28).
 *
 * **`edit(…, settled = false)` per event and `settle()` on release** — the
 * `DetentSlider` contract, so a tool declaring `Continuous` gets one history entry
 * per gesture without this file knowing anything about undo. Double-click returns to
 * the default, exactly as a slider's double-click returns to its detent.
 *
 * **The vertical axis is inverted.** A pointer's y grows downward and Tone grows
 * upward, because up is more light everywhere a person has met a pad before.
 */
@Composable
internal fun PadCanvas(spec: PadParam, params: ParamSet, writer: ParamWriter) {
    val point = params[spec]
    val side = with(LocalDensity.current) { PAD_SIZE.toPx() }
    // Where the pointer is, in pad pixels: `detectDragGestures` reports a start and
    // then deltas, so the absolute position is accumulated rather than read back out
    // of a composition that has not recomposed yet.
    var at by remember { mutableStateOf(Offset.Zero) }
    val crosshair = Theme.viewer.onScrimBorder
    val axisStyle = TextStyle(color = Theme.viewer.onScrimSecondary, fontSize = AXIS_SIZE)

    fun write(position: Offset) = writer.edit(spec, spec.pointAt(position, side), settled = false)

    Box(
        modifier = Modifier
            .size(PAD_SIZE)
            .clip(RoundedCornerShape(PAD_RADIUS))
            .background(Theme.viewer.onScrimHover)
            .border(1.dp, crosshair, RoundedCornerShape(PAD_RADIUS))
            .pointerHoverIcon(PointerIcon.Hand)
            .testTag("param-${spec.key}")
            .pointerInput(spec.key) {
                detectTapGestures(onDoubleTap = { writer.edit(spec, spec.default, settled = true) })
            }
            .pointerInput(spec.key) {
                detectDragGestures(
                    onDragStart = { start ->
                        at = start
                        write(at)
                    },
                    // A cancelled drag settles what it already wrote rather than
                    // abandoning it: the value on screen is the one the user let go
                    // of, and taking it back would look like a dropped gesture.
                    onDragEnd = writer::settle,
                    onDragCancel = writer::settle
                ) { change, dragged ->
                    at += dragged
                    write(at)
                    change.consume()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPadFace(crosshair, spec.handleAt(point, size.minDimension), HANDLE_RADIUS.toPx())
        }
        BasicText(
            text = spec.yLabel,
            style = axisStyle,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = AXIS_INSET)
        )
        BasicText(
            text = spec.xLabel,
            style = axisStyle,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = AXIS_INSET)
        )
    }
}

/** Cross-hairs through the middle and a white handle at the value. */
internal fun DrawScope.drawPadFace(
    crosshair: Color,
    handle: Offset,
    handleRadius: Float
) {
    drawLine(crosshair, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f))
    drawLine(crosshair, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height))
    drawCircle(Color.White, handleRadius, handle)
}

/** Where a pointer inside a [side]-pixel pad lands, in the spec's own units. */
internal fun PadParam.pointAt(position: Offset, side: Float): ParamPoint {
    val across = (position.x / side).coerceIn(0f, 1f)
    val down = (position.y / side).coerceIn(0f, 1f)
    return ParamPoint(
        x = xRange.start + across * (xRange.endInclusive - xRange.start),
        y = yRange.endInclusive - down * (yRange.endInclusive - yRange.start)
    )
}

/** The inverse: where the handle for [point] sits, in pad pixels. */
internal fun PadParam.handleAt(point: ParamPoint, side: Float): Offset =
    Offset(fraction(point.x, xRange) * side, (1f - fraction(point.y, yRange)) * side)

private fun fraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    return if (span == 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
}

/** Large enough to aim in — it costs the panel nothing, sitting in a popover. */
private val PAD_SIZE = 120.dp
private val PAD_RADIUS = 10.dp
private val HANDLE_RADIUS = 5.dp
private val AXIS_INSET = 5.dp
private val AXIS_SIZE = 9.sp
