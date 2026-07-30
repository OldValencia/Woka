package com.woka.ui.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.data.ScalePreset
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val PRESET_ORDER = listOf(ScalePreset.Fit, ScalePreset.Fill, ScalePreset.Original)

private fun cyclePreset(current: ScalePreset, step: Int): ScalePreset {
    val idx = PRESET_ORDER.indexOf(current)
    return PRESET_ORDER[(idx + step + PRESET_ORDER.size) % PRESET_ORDER.size]
}

private fun presetIcon(preset: ScalePreset) = when (preset) {
    ScalePreset.Fit -> Icons.Static.ScaleFit
    ScalePreset.Fill -> Icons.Static.ScaleFill
    ScalePreset.Original -> Icons.Static.ScaleOriginal
}

private fun presetLabel(preset: ScalePreset) = when (preset) {
    ScalePreset.Fit -> "Fit"
    ScalePreset.Fill -> "Fill"
    ScalePreset.Original -> "Actual"
}

/** Runs the state-dependent central-click logic described in the spec. */
private fun onCenterClick(
    mode: SizeMode,
    savedPreset: ScalePreset,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit
) {
    when (mode) {
        is SizeMode.Zoom ->
            if (mode.percent != SizeMode.DEFAULT_PERCENT) {
                onSizeModeChange(SizeMode.Zoom(SizeMode.DEFAULT_PERCENT)); onOffsetReset()
            } else onSizeModeChange(SizeMode.Preset(savedPreset))
        is SizeMode.Preset ->
            if (mode.preset != ScalePreset.Fit) {
                onSizeModeChange(SizeMode.Preset(ScalePreset.Fit)); onOffsetReset()
            } else onSizeModeChange(SizeMode.Zoom(SizeMode.DEFAULT_PERCENT))
    }
}

private fun centerTooltip(mode: SizeMode): String = when (mode) {
    is SizeMode.Zoom -> if (mode.percent != SizeMode.DEFAULT_PERCENT) "Reset to 100%" else "Switch to presets"
    is SizeMode.Preset -> if (mode.preset != ScalePreset.Fit) "Reset to Fit" else "Switch to zoom"
}

@Composable
fun SizeModeIndicator(
    sizeMode: SizeMode,
    savedPreset: ScalePreset,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetReset: () -> Unit
) {
    var slideDir by remember { mutableStateOf(1) }
    AnimatedContent(
        targetState = sizeMode,
        transitionSpec = {
            (slideInHorizontally(tween(220)) { w -> slideDir * w } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(220)) { w -> -slideDir * w } + fadeOut(tween(220)))
        },
        contentKey = { m -> if (m is SizeMode.Zoom) "zoom" else "preset:${(m as SizeMode.Preset).preset}" }
    ) { mode ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            when (mode) {
                is SizeMode.Zoom -> {
                    StepButton("-") { onSizeModeChange(SizeMode.Zoom(SizeMode.clampPercent(mode.percent - 1))) }
                    // The percentage is a passive label + hover trigger for the popover;
                    // the click action (reset / switch to presets) lives inside the popover.
                    Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        IndicatorText("${mode.percent}%")
                    }
                    StepButton("+") { onSizeModeChange(SizeMode.Zoom(SizeMode.clampPercent(mode.percent + 1))) }
                }
                is SizeMode.Preset -> {
                    ArrowButton(Icons.Static.ChevronLeft) { slideDir = -1; onSizeModeChange(SizeMode.Preset(cyclePreset(mode.preset, -1))) }
                    CenterButton(centerTooltip(mode), { slideDir = 1; onCenterClick(mode, savedPreset, onSizeModeChange, onOffsetReset) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                painter = painterResource(presetIcon(mode.preset)),
                                contentDescription = null,
                                tint = Theme.colors.textPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            IndicatorText(presetLabel(mode.preset))
                        }
                    }
                    ArrowButton(Icons.Static.ChevronRightMac) { slideDir = 1; onSizeModeChange(SizeMode.Preset(cyclePreset(mode.preset, 1))) }
                }
            }
        }
    }
}

@Composable
private fun IndicatorText(text: String) {
    Text(
        text = text,
        style = TextStyle(color = Theme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.offset(y = (-1).dp)
    )
}

@Composable
private fun CenterButton(tooltip: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val body: @Composable () -> Unit = {
        Box(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onClick() }.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) { content() }
    }
    if (tooltip.isBlank()) body() else ViewerTooltip(tooltip) { body() }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = TextStyle(color = Theme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.offset(y = (-1.5).dp)
        )
    }
}

@Composable
private fun ArrowButton(icon: DrawableResource, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Theme.colors.textPrimary,
            modifier = Modifier.size(11.dp)
        )
    }
}
