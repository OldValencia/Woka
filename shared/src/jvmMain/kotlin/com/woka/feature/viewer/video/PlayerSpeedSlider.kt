package com.woka.feature.viewer.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.woka.core.ui.theme.Theme
import com.woka.core.data.PlaybackSpeed
import com.woka.core.ui.components.DetentSlider
import com.woka.core.ui.components.DetentSliderColors

private val SPAN = PlaybackSpeed.MAX - PlaybackSpeed.MIN

private fun fractionOf(speed: Float): Float = (speed - PlaybackSpeed.MIN) / SPAN

private fun speedAt(fraction: Float): Float =
    PlaybackSpeed.snap(PlaybackSpeed.MIN + fraction * SPAN)

/**
 * Playback-speed slider (§6): a thin track with a **detent dot at 1×** that the
 * knob snaps onto, so returning to normal speed is a gesture rather than a target
 * you have to hit exactly. Snapping is done by [PlaybackSpeed.snap], shared with
 * the persistence layer so a stored value can never sit just off 1×.
 */
@Composable
internal fun PlayerSpeedSlider(speed: Float, onSpeedChange: (Float) -> Unit) {
    // Live drag value. The knob follows the finger, but the *player* only hears
    // the final one: every committed change re-seeks the audio grabber and
    // rebuilds the atempo graph, and a drag emits a dozen of those (§6).
    var dragSpeed by remember { mutableStateOf<Float?>(null) }

    fun commitDrag() {
        dragSpeed?.let(onSpeedChange)
        dragSpeed = null
    }

    PlayerSpeedHeader(dragSpeed ?: speed)

    DetentSlider(
        fraction = fractionOf(dragSpeed ?: speed),
        // The same four roles the editor's slider reads (ME-161), so both sliders in the
        // app take one palette rather than two sets of alphas somebody once picked.
        colors = DetentSliderColors(
            track = Theme.viewer.onScrimHover,
            detent = Theme.viewer.onScrimBorder,
            knob = Theme.viewer.onScrim
        ),
        onTap = { onSpeedChange(speedAt(it)) },
        detents = listOf(fractionOf(PlaybackSpeed.NORMAL)),
        onDragStart = { dragSpeed = speedAt(it) },
        onDrag = { dragSpeed = speedAt(it) },
        onDragEnd = { commitDrag() }
    )
}
