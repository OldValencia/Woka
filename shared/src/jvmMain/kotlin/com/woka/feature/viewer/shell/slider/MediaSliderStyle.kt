package com.woka.feature.viewer.shell.slider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * Everything about how [MediaSlider] looks, as one value.
 *
 * Seven of the slider's fourteen parameters were styling passed one at a time —
 * three of them by the single caller, four left on defaults. They vary together or
 * not at all, so they travel together.
 */
@Immutable
data class MediaSliderStyle(
    val activeColor: Color,
    val inactiveColor: Color,
    val thumbColor: Color,
    val trackHeight: Dp = 3.dp,
    val thumbSize: Dp = 8.dp,
    val thumbSizeDragged: Dp = 15.dp,
    val hitAreaHeight: Dp = 24.dp
)

/** The player's time bar: accent for what has played, secondary for what has not. */
@Composable
fun defaultMediaSliderStyle(): MediaSliderStyle = MediaSliderStyle(
    activeColor = Theme.colors.accent,
    inactiveColor = Theme.colors.textSecondary,
    thumbColor = Theme.colors.accent
)
