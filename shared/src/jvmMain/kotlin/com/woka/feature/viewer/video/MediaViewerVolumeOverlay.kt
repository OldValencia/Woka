package com.woka.feature.viewer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.PopoverAnchor
import com.woka.feature.viewer.shell.ToolbarPopover
import com.woka.feature.viewer.video.model.MediaTrack
import com.woka.feature.viewer.shell.slider.MediaVerticalSlider

@Composable
fun VolumeVerticalOverlay(
    isVisible: Boolean,
    volume: Float,
    bottomPadding: Dp,
    alignment: Alignment = Alignment.BottomEnd,
    audioTracks: List<MediaTrack> = emptyList(),
    selectedAudioIndex: Int? = null,
    onSelectAudio: (Int) -> Unit = {},
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
                        .background(Color.White.copy(alpha = 0.15f))
                )
            }
            VolumeColumn(volume, onVolumeChange, onHover)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VolumeColumn(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onHover: (Boolean) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.first().scrollDelta.y
                        val volDelta = if (delta > 0) -0.05f else 0.05f
                        onVolumeChange((volume + volDelta).coerceIn(0f, 1f))
                        onHover(true) // reset hover/timer
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    ) {
        Text(
            text = "${(volume * 100).toInt()}%",
            color = Color.White,
            style = Theme.viewer.overlayLabel,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        MediaVerticalSlider(
            value = volume,
            onValueChange = onVolumeChange,
            modifier = Modifier.width(16.dp).height(80.dp)
        )
    }
}
