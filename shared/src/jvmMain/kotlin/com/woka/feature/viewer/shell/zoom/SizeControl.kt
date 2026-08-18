package com.woka.feature.viewer.shell.zoom

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.woka.core.data.ScalePreset
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.clampPercent
import com.woka.feature.viewer.shell.percentIn
import com.woka.feature.viewer.shell.sizeModeFor
import com.woka.feature.viewer.shell.stepDown
import com.woka.feature.viewer.shell.stepUp
import com.woka.feature.viewer.shell.zoomBounds

/**
 * Hosts the inline [SizeIndicatorButton] and the [ZoomMenuPopover] it opens.
 *
 * Opening is a **click**, not hover: the menu is focusable, which is what lets it
 * hold a real text field. The previous hover popover had to be non-focusable (a
 * focusable popup swallows scene pointer input and makes hover blink), so typing
 * was captured on the anchor a digit at a time — all of that is gone.
 */
@Composable
fun SizeControl(
    sizeMode: SizeMode,
    containerSize: IntSize,
    nativeSize: IntSize,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit,
    /**
     * The reachable range, when the owner's is narrower than the viewer's — the editor
     * floors at Fit (see `CanvasZoom`). A slider that travels somewhere the picture will
     * not follow reads as the zoom being broken, not as a limit.
     */
    bounds: IntRange? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    val gapPx = with(LocalDensity.current) { 6.dp.roundToPx() }

    val range = bounds ?: zoomBounds(containerSize, nativeSize)
    val percent = sizeMode.percentIn(containerSize, nativeSize)
    val label = when (sizeMode) {
        is SizeMode.Preset -> "${presetLabel(sizeMode.preset)} · $percent%"
        is SizeMode.Zoom -> "$percent%"
    }

    fun applyPercent(value: Int) {
        onSizeModeChange(sizeModeFor(clampPercent(value, range), containerSize, nativeSize))
    }

    fun applyPreset(preset: ScalePreset) {
        onSizeModeChange(SizeMode.Preset(preset))
        if (preset == ScalePreset.Fit) onOffsetReset()
        menuOpen = false
    }

    Box(modifier = Modifier.onSizeChanged { anchorHeight = it.height }) {
        SizeIndicatorButton(
            label = label,
            menuOpen = menuOpen,
            onStepDown = { applyPercent(stepDown(percent, range)) },
            onStepUp = { applyPercent(stepUp(percent, range)) },
            onToggleMenu = { menuOpen = !menuOpen }
        )

        if (menuOpen) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, anchorHeight + gapPx),
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true)
            ) {
                ZoomMenuPopover(
                    sizeMode = sizeMode,
                    percent = percent,
                    bounds = range,
                    container = containerSize,
                    native = nativeSize,
                    onPreset = { applyPreset(it) },
                    onPercent = { applyPercent(it) },
                    onReset = {
                        onSizeModeChange(SizeMode.Preset(ScalePreset.Fit))
                        onOffsetReset()
                    },
                    onDismiss = { menuOpen = false }
                )
            }
        }
    }
}
