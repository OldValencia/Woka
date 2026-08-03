package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.data.ScalePreset
import com.woka.ui.theme.Theme

private val MENU_WIDTH = 220.dp

/**
 * The single entry point for size: the three snap points with the percentage each
 * currently resolves to, the scale itself, and manual entry — one surface instead
 * of a mode switch hidden in two unrelated places.
 */
@Composable
internal fun ZoomMenuPopover(
    sizeMode: SizeMode,
    percent: Int,
    bounds: IntRange,
    container: IntSize,
    native: IntSize,
    onPreset: (ScalePreset) -> Unit,
    onPercent: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(MENU_WIDTH)
            .shadow(12.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(14.dp))
            .background(Theme.colors.surface)
            .border(1.dp, Theme.colors.border, RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(ScalePreset.Fit, ScalePreset.Fill, ScalePreset.Original).forEach { preset ->
            ZoomPresetRow(
                preset = preset,
                percent = presetPercent(preset, container, native),
                selected = sizeMode is SizeMode.Preset && sizeMode.preset == preset,
                onClick = { onPreset(preset) }
            )
        }

        Box(
            modifier = Modifier
                .padding(vertical = 5.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Theme.colors.border)
        )

        ZoomMenuSlider(
            percent = percent,
            bounds = bounds,
            container = container,
            native = native,
            onPercent = onPercent
        )

        ZoomMenuInputRow(
            percent = percent,
            bounds = bounds,
            canReset = sizeMode !is SizeMode.Preset || sizeMode.preset != ScalePreset.Fit,
            onPercent = onPercent,
            onReset = onReset,
            onCommit = onDismiss
        )
    }
}
