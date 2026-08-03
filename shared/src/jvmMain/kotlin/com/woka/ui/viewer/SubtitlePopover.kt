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
import com.woka.ui.viewer.player.ExternalSubtitle
import com.woka.ui.viewer.player.MediaTrack
import com.woka.ui.viewer.player.SubtitleSelection

@Composable
internal fun SubtitlePopoverList(
    tracks: List<MediaTrack>,
    external: List<ExternalSubtitle>,
    selection: SubtitleSelection,
    onSelect: (SubtitleSelection) -> Unit,
    onAddFile: () -> Unit,
    onRemoveFile: (String) -> Unit,
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
            PopoverListRow(
                label = "Off",
                selected = selection == SubtitleSelection.Off,
                onClick = { onSelect(SubtitleSelection.Off) }
            )
            tracks.forEach { track ->
                PopoverListRow(
                    label = track.label,
                    selected = selection.embeddedIndex == track.index,
                    onClick = { onSelect(SubtitleSelection.Embedded(track.index)) }
                )
            }
            // Sidecars are listed after the embedded tracks and marked with their
            // format, so it is obvious which entries came from a file on disk.
            external.forEach { file ->
                PopoverListRow(
                    label = "${file.label} · ${file.format}",
                    selected = selection.externalPath == file.path,
                    onClick = { onSelect(SubtitleSelection.External(file.path)) },
                    // Every sidecar gets the ✕ — the row does not look different
                    // depending on where the file happens to live, so it must not
                    // behave differently either. It hides, never deletes.
                    onRemove = { onRemoveFile(file.path) }
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = Color.White.copy(alpha = 0.15f)
        )
        SubtitleActionRow(label = "Add subtitle file…", onClick = onAddFile)
        SubtitleActionRow(label = "Customize Subtitles", onClick = onCustomize)
    }
}

/**
 * Accent-coloured action row at the foot of the list. Kept separate from
 * [PopoverListRow]: it is an action, never a selection, so it has no selected
 * state and always renders in the accent colour.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SubtitleActionRow(label: String, onClick: () -> Unit) {
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
            text = label,
            style = TextStyle(
                color = Theme.colors.accent,
                fontSize = 12.sp
            ),
            animate = hovered,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
