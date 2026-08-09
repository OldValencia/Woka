package com.woka.feature.viewer.shell.slider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Decides whether the pill is up, where it sits, and reports that position back.
 *
 * Split out of [MediaSlider] under the §2 file-size mandate when the preview
 * parameters arrived. It earns the split: the pill is the one part of the slider
 * with an outward-facing side effect — [onPositionChange] is what drives a preview
 * decoder — and having that live beside the pill's own geometry keeps the slider
 * itself a control that draws a track and a thumb.
 *
 * [hoverX] falls back to [dragX] so the pill survives the pointer leaving the row
 * mid-scrub, which is exactly when it is being read.
 */
@Composable
internal fun SliderPill(
    hoverX: Float?,
    dragX: Float?,
    width: Float,
    tooltipText: ((Float) -> String)?,
    chapterTitle: String?,
    preview: SliderPreview? = null,
    onPositionChange: ((Float?) -> Unit)? = null
) {
    val pillX = (hoverX ?: dragX)?.takeIf { width > 0f }
    val fraction = pillX?.let { (it / width).coerceIn(0f, 1f) }

    // Reported from an effect rather than from composition: the callback starts
    // decode work, and composition can run any number of times for one position.
    LaunchedEffect(fraction) { onPositionChange?.invoke(fraction) }

    // Leaving composition does not run the effect above, so a slider torn down with
    // the pointer still on it would leave the caller believing it is hovered — which
    // for a caller using this to hold chrome open means chrome that never hides again.
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    DisposableEffect(Unit) { onDispose { currentOnPositionChange?.invoke(null) } }

    if (tooltipText == null || pillX == null || fraction == null) return
    MediaSliderTooltip(
        x = pillX.coerceIn(0f, width),
        timeText = tooltipText(fraction),
        chapterTitle = chapterTitle,
        preview = preview
    )
}
