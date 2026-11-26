package com.woka.feature.editor.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip
import java.awt.Cursor
import java.util.Locale

private const val DIAL_TIP = "Drag sideways to level the horizon. Double-click to reset."

/** The scale's glide to a written value (`.dial:not(.dragging) .dial-scale`, 320 ms). */
private const val GLIDE_MS = 320

/** The needle's pulse on arriving at zero, and how far it stretches (`paintDial`). */
private const val PULSE_MS = 240
private const val PULSE_STRETCH = 1.35f

/**
 * A `FloatParam` with `dial` — Straighten's angle (RD-205, `.dial`, `instruments.js`).
 *
 * **Not a scrub row**: a row maps the pointer to an absolute place on a track, and a horizon
 * is levelled by small nudges watched on the picture. Here the scale moves under a fixed
 * needle, 8 dp to the degree, and the pointer moves the scale — a drag right turns the
 * value down, as a wheel would. The writing, settling and reset are the scrub rows' own
 * ([ScrubLevel]), so a drag is one history entry and a double-click goes to the detent.
 *
 * **The keyboard through the editor's caret slot** (`CanvasKeys.fieldFocused`): the editor's
 * handler is previewed at the window root and steps frames on ← / →, so a focused dial must
 * say it is taking those keys, as a value field does. Each press is a settled write.
 */
@Composable
internal fun ParamDial(level: ScrubLevel) {
    val spec = level.spec
    // A gesture outlives the composition it started in; it reads the level on screen now.
    val current by rememberUpdatedState(level)
    val room = Theme.room
    val keys = LocalCanvasKeys.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(
        targetValue = level.value,
        animationSpec = if (dragging) snap() else tween(GLIDE_MS, easing = EditorTokens.Motion.easing)
    )
    val needle = remember { Animatable(1f) }
    val zero = level.value == 0f
    var wasZero by remember { mutableStateOf(zero) }
    LaunchedEffect(zero) {
        if (zero && !wasZero) {
            needle.animateTo(PULSE_STRETCH, tween(PULSE_MS / 2))
            needle.animateTo(1f, tween(PULSE_MS / 2))
        }
        wasZero = zero
    }
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel(spec.label)
        ViewerTooltip(DIAL_TIP) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(room.well)
                    .background(if (hovered || dragging) room.hover else Color.Transparent)
                    .border(2.dp, if (focused) room.accentLine else Color.Transparent, shape)
                    .hoverable(hover)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                    .focusRequester(focus)
                    .onFocusChanged {
                        focused = it.isFocused
                        keys?.fieldFocused = it.isFocused
                    }
                    .onPreviewKeyEvent { event -> dialKey(event, current) }
                    .focusable()
                    .pointerInput(spec.key) {
                        detectTapGestures(onPress = { focus.requestFocus() }, onDoubleTap = { current.reset() })
                    }
                    .pointerInput(spec.key) { dialDrag({ current }, focus) { dragging = it } }
                    .padding(top = 10.dp, bottom = 6.dp)
                    .testTag("param-${spec.key}")
            ) {
                BasicText(
                    text = String.format(Locale.ROOT, "%.1f°", level.value),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("param-${spec.key}-readout"),
                    style = TextStyle(
                        color = if (zero) room.muted else room.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                )
                DialScale(spec, shown, needle.value)
            }
        }
    }
}
