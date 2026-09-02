package com.woka.feature.viewer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.Marquee
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.video.model.MediaTrack

/** Audio-track selection list docked to the side of the volume popover (§2.1). */
@Composable
fun AudioTrackExtension(
    tracks: List<MediaTrack>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .heightIn(max = 168.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tracks.forEach { track ->
            AudioTrackRow(
                label = track.label,
                selected = track.index == selectedIndex,
                onClick = { onSelect(track.index) }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AudioTrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val background = when {
        selected -> Theme.colors.accent.copy(alpha = 0.30f)
        hovered -> Theme.viewer.onScrimHover
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Marquee(
            text = label,
            style = TextStyle(
                color =
                    if (selected) Theme.viewer.onScrim else Theme.viewer.onScrimSecondary,
                fontSize = 12.sp
            ),
            animate = hovered || selected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
