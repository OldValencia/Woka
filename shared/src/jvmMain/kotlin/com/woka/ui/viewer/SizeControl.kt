package com.woka.ui.viewer

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.woka.data.ScalePreset
import com.woka.data.ZoomPopoverView
import kotlinx.coroutines.delay

private fun digitOf(k: Key): Char? = when (k) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/**
 * Hosts the [SizeModeIndicator] and, in Zoom Mode, a hover [ZoomPopover] below it.
 *
 * The popover is **non-focusable** so it never steals scene focus — a focusable popup
 * intercepts pointer input scene-wide and makes hover blink (and glitches on RMB-hold).
 * Manual typing therefore can't live in the popup; instead the keyboard is captured on
 * the indicator itself (in the main window) while the Input view is open.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SizeControl(
    sizeMode: SizeMode,
    savedPreset: ScalePreset,
    popoverView: ZoomPopoverView,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit,
    onPopoverViewChange: (ZoomPopoverView) -> Unit
) {
    var hoveredRaw by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    val editFocus = remember { FocusRequester() }
    val gapPx = with(LocalDensity.current) { 6.dp.roundToPx() }

    LaunchedEffect(hoveredRaw) {
        if (hoveredRaw) expanded = true else { delay(350); expanded = false }
    }

    val zoom = sizeMode as? SizeMode.Zoom
    val inputActive = expanded && zoom != null && popoverView == ZoomPopoverView.Input

    // When the Input view opens, seed the field and grab keyboard focus on the anchor.
    LaunchedEffect(inputActive) {
        if (inputActive) { inputText = zoom!!.percent.toString(); editFocus.requestFocus() }
    }
    // Keep the field in sync if the value changes from elsewhere (scroll / slider).
    LaunchedEffect(zoom?.percent) {
        if (inputActive && inputText.toIntOrNull() != zoom?.percent) inputText = zoom?.percent?.toString().orEmpty()
    }

    fun apply(text: String) {
        text.toIntOrNull()?.let { if (it in SizeMode.MIN_PERCENT..SizeMode.MAX_PERCENT) onSizeModeChange(SizeMode.Zoom(it)) }
    }

    Box(
        modifier = Modifier
            .onSizeChanged { anchorHeight = it.height }
            .onPointerEvent(PointerEventType.Enter) { hoveredRaw = true }
            .onPointerEvent(PointerEventType.Exit) { hoveredRaw = false }
            .focusRequester(editFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (!inputActive || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.Backspace -> { inputText = inputText.dropLast(1); apply(inputText); true }
                    Key.Enter, Key.NumPadEnter, Key.Escape -> { hoveredRaw = false; expanded = false; true }
                    else -> {
                        val d = digitOf(ev.key)
                        if (d != null) { inputText = (inputText + d).take(4); apply(inputText); true } else false
                    }
                }
            }
    ) {
        SizeModeIndicator(sizeMode, savedPreset, onSizeModeChange, onOffsetReset)

        if (zoom != null && expanded) {
            val atDefault = zoom.percent == SizeMode.DEFAULT_PERCENT
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, anchorHeight + gapPx),
                properties = PopupProperties(focusable = false)
            ) {
                ZoomPopover(
                    percent = zoom.percent,
                    view = popoverView,
                    inputText = inputText,
                    actionLabel = if (atDefault) "Switch to presets" else "Reset to 100%",
                    onAction = {
                        if (atDefault) onSizeModeChange(SizeMode.Preset(savedPreset))
                        else { onSizeModeChange(SizeMode.Zoom(SizeMode.DEFAULT_PERCENT)); onOffsetReset() }
                        hoveredRaw = false; expanded = false
                    },
                    onPercent = { onSizeModeChange(SizeMode.Zoom(SizeMode.clampPercent(it))) },
                    onView = onPopoverViewChange,
                    onHoverChange = { hoveredRaw = it }
                )
            }
        }
    }
}
