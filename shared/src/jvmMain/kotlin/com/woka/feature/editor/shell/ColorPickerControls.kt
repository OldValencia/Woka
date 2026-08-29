package com.woka.feature.editor.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The two gradients a colour is picked on (ME-114) — split from `ColorPickerPopover` at the
 * seam between the panel and the controls inside it.
 */

private val PAD_HEIGHT = 132.dp
private val HUE_HEIGHT = 16.dp

/** The full hue sweep. Seven stops, so the gradient closes back on red at 360°. */
private val HUE_STOPS = List(7) { Color.hsv(it * 60f % 360f, 1f, 1f) }

private val MARKER = Stroke(width = 2f)

/**
 * Saturation left to right, brightness top to bottom — the arrangement every picker on
 * this platform uses, so the gesture is known before it is learned.
 *
 * The hue drawn here comes from the *picker's* state, never from the colour it produced:
 * a pick in the black corner has no hue to read back, which is what made the first version
 * snap to red (`ColorPickerPopover`).
 */
@Composable
internal fun SaturationValuePad(hsv: Hsv, onPick: (Float, Float) -> Unit) {
    val pure = Color.hsv(hsv.hue, 1f, 1f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(PAD_HEIGHT)
            .pickable("color-pad") { fraction -> onPick(fraction.x, 1f - fraction.y) }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
        // Over the saturation ramp rather than beside it: brightness is a second axis on
        // the same square, not a second square.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        drawMarker(Offset(size.width * hsv.saturation, size.height * (1f - hsv.value)))
    }
}

@Composable
internal fun HueStrip(hsv: Hsv, onPick: (Float) -> Unit) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(HUE_HEIGHT)
            .pickable("color-hue") { fraction -> onPick(fraction.x * 360f) }
    ) {
        drawRect(Brush.horizontalGradient(HUE_STOPS))
        drawMarker(Offset(size.width * (hsv.hue / 360f), size.height / 2f))
    }
}

/**
 * The pad and the strip together, with the three numbers they are arranged in held here
 * (ME-155) — what a compact colour button unfolds into when its rainbow is pressed.
 *
 * A colour cannot carry a hue: black, white and every grey have none, so deriving the
 * arrangement from the stored value each frame threw it away in the dark corner and
 * snapped the panel back to red. `ColorPickerPopover` records that bug at length; this is
 * the same fix, kept in one place so the popover and the compact button cannot diverge.
 */
@Composable
internal fun HandPicker(argb: Long, onPick: (Long) -> Unit) {
    val opened = remember { argb }
    var hsv by remember(opened) { mutableStateOf(hsvOf(opened)) }
    val write = { next: Hsv ->
        hsv = next
        onPick(packHsv(next.hue, next.saturation, next.value))
    }
    SaturationValuePad(hsv) { s, v -> write(hsv.copy(saturation = s, value = v)) }
    HueStrip(hsv) { hue -> write(hsv.copy(hue = hue)) }
}

/**
 * The chosen point as a ring: a filled dot hides the colour it is reporting, and at the
 * pad's white corner a white ring alone would vanish — hence the dark one under it.
 */
private fun DrawScope.drawMarker(at: Offset) {
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = 7f, center = at, style = MARKER)
    drawCircle(Color.White, radius = 6f, center = at, style = MARKER)
}

/**
 * A press, a drag **and** the press that begins a drag all report the position as a 0..1
 * fraction of the control.
 *
 * `onDragStart` is the part that was missing and the reason a first click did nothing:
 * `detectDragGestures` reports movement only, and `detectTapGestures` fires on *release*,
 * so pressing and sliding in one motion — which is how anyone uses a colour pad — began
 * with the marker still where it was and picked nothing until the pointer moved.
 */
private fun Modifier.pickable(tag: String, onPick: (Offset) -> Unit): Modifier = this
    .clip(RoundedCornerShape(6.dp))
    .pointerHoverIcon(PointerIcon.Crosshair)
    .testTag(tag)
    .pointerInput(Unit) {
        detectTapGestures { at -> onPick(at.asFraction(size.width, size.height)) }
    }
    .pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { at -> onPick(at.asFraction(size.width, size.height)) }
        ) { change, _ ->
            onPick(change.position.asFraction(size.width, size.height))
        }
    }

private fun Offset.asFraction(width: Int, height: Int) = Offset(
    x = (x / width.toFloat()).coerceIn(0f, 1f),
    y = (y / height.toFloat()).coerceIn(0f, 1f)
)
