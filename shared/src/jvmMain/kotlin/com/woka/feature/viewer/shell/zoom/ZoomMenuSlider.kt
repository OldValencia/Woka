package com.woka.feature.viewer.shell.zoom

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ScalePreset
import com.woka.core.ui.components.DetentSlider
import com.woka.core.ui.components.DetentSliderColors
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.percentFromFraction
import com.woka.feature.viewer.shell.presetPercent
import com.woka.feature.viewer.shell.sliderFraction

/**
 * Zoom track. **Logarithmic** — on a linear track the whole useful region below
 * 100% collapses into a tenth of the travel. Fit / Fill / Actual are drawn as
 * detent dots at their current percentages, the same visual language as the 1×
 * detent in PlayerSpeedSlider — which is now literally the same widget.
 */
@Composable
internal fun ZoomMenuSlider(
    percent: Int,
    bounds: IntRange,
    container: IntSize,
    native: IntSize,
    onPercent: (Int) -> Unit
) {
    val detents = listOf(ScalePreset.Fit, ScalePreset.Fill, ScalePreset.Original).map { preset ->
        sliderFraction(presetPercent(preset, container, native), bounds)
    }

    DetentSlider(
        fraction = sliderFraction(percent, bounds),
        colors = DetentSliderColors(
            track = Theme.colors.border,
            detent = Theme.colors.textSecondary.copy(alpha = 0.55f),
            knob = Theme.colors.textPrimary,
            fill = Theme.colors.textPrimary.copy(alpha = 0.55f)
        ),
        // Every drag position is committed: the zoom has no cost to re-apply, so
        // the picture tracks the finger. Speed commits on release instead.
        onTap = { onPercent(percentFromFraction(it, bounds)) },
        detents = detents,
        gestureKey = bounds
    )
}
