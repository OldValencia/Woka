package com.woka.feature.viewer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.data.volumeCeiling
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.PopoverAnchor
import com.woka.feature.viewer.shell.ToolbarPopover
import com.woka.feature.viewer.shell.slider.MediaVerticalSlider
import com.woka.feature.viewer.video.model.MediaTrack

/** Where the volume slider is driven from, for the UI tests. */
internal const val VOLUME_SLIDER_TAG = "volume-slider"

@Composable
fun VolumeVerticalOverlay(
    isVisible: Boolean,
    volume: Float,
    bottomPadding: Dp,
    alignment: Alignment = Alignment.BottomEnd,
    audioTracks: List<MediaTrack> = emptyList(),
    selectedAudioIndex: Int? = null,
    onSelectAudio: (Int) -> Unit = {},
    soundBoost: Boolean = false,
    onSoundBoostChange: (Boolean) -> Unit = {},
    onVolumeChange: (Float) -> Unit,
    onHover: (Boolean) -> Unit
) {
    ToolbarPopover(
        isVisible = isVisible,
        endPadding = PopoverAnchor.Volume,
        bottomPadding = bottomPadding,
        alignment = alignment,
        onHover = onHover,
        // Sizes to content: the optional audio-track list docks onto its side.
        width = null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Multiple audio tracks → selection list docks onto the popover (§2.1).
            if (audioTracks.size > 1) {
                AudioTrackExtension(audioTracks, selectedAudioIndex, onSelectAudio)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(96.dp)
                        .background(Theme.viewer.onScrimBorder)
                )
            }
            VolumeColumn(volume, soundBoost, onVolumeChange, onSoundBoostChange, onHover)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VolumeColumn(
    volume: Float,
    soundBoost: Boolean,
    onVolumeChange: (Float) -> Unit,
    onSoundBoostChange: (Boolean) -> Unit,
    onHover: (Boolean) -> Unit
) {
    // The switch permits the range; it never raises the level itself, so the ceiling is
    // the only thing it changes here.
    val ceiling = volumeCeiling(soundBoost)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pointerInput(ceiling) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.first().scrollDelta.y
                        val volDelta = if (delta > 0) -0.05f else 0.05f
                        onVolumeChange((volume + volDelta).coerceIn(0f, ceiling))
                        onHover(true) // reset hover/timer
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    ) {
        BasicText(
            text = "${(volume * 100).toInt()}%",
            modifier = Modifier.padding(bottom = 8.dp),
            style = Theme.viewer.overlayLabel.copy(
                // The reading takes the bar's own colour past 100 %, so the number and
                // the track never disagree about whether this is the range that clips.
                color = boostLabelColor(volume, ceiling) ?: Theme.viewer.onScrim
            )
        )
        MediaVerticalSlider(
            value = volume,
            onValueChange = onVolumeChange,
            modifier = Modifier.width(16.dp).height(80.dp).testTag(VOLUME_SLIDER_TAG),
            ceiling = ceiling,
            // Only with boost on: a detent on a range that ends at 100 % would snap the
            // top of the ordinary slider to itself.
            detent = 1f.takeIf { soundBoost },
            fill = { fraction -> VolumeSliderFill(fraction, ceiling) }
        )
        SoundBoostRow(enabled = soundBoost, onChange = onSoundBoostChange)
    }
}
