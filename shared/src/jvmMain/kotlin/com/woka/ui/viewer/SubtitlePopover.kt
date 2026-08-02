package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import com.woka.ui.components.Marquee
import com.woka.ui.theme.Theme
import com.woka.ui.viewer.player.MediaTrack

@Composable
internal fun SubtitlePopoverList(
    tracks: List<MediaTrack>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    onCustomize: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 168.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SubtitleTrackRow(
                label = "Off",
                selected = selectedIndex == null,
                onClick = { onSelect(null) }
            )
            tracks.forEach { track ->
                SubtitleTrackRow(
                    label = track.label,
                    selected = track.index == selectedIndex,
                    onClick = { onSelect(track.index) }
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = Color.White.copy(alpha = 0.15f)
        )
        SubtitleCustomizeRow(onClick = onCustomize)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SubtitleTrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    val background = when {
        selected -> Theme.colors.accent.copy(alpha = 0.30f)
        hovered -> Color.White.copy(alpha = 0.10f)
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
                color = Color.White.copy(alpha = if (selected) 1f else 0.72f),
                fontSize = 12.sp
            ),
            animate = hovered || selected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SubtitleCustomizeRow(onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val background = if (hovered) Color.White.copy(alpha = 0.10f) else Color.Transparent
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
            text = "Customize Subtitles",
            style = TextStyle(
                color = Theme.colors.accent,
                fontSize = 12.sp
            ),
            animate = hovered,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
