package com.woka.feature.viewer.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.woka.feature.viewer.video.model.ExternalSubtitle
import com.woka.feature.viewer.video.model.MediaTrack
import com.woka.feature.viewer.video.model.SubtitleSelection
import com.woka.feature.viewer.video.engine.isBitmapSubtitleFile
import com.woka.feature.viewer.shell.PopoverListRow

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
        // A PGS / VobSub cue is a finished picture — §3 restyles text, so on those
        // the row would open a panel whose every control does nothing.
        val isBitmap = isBitmapSelection(selection, tracks)
        SubtitleActionRow(
            label = "Customize Subtitles",
            onClick = onCustomize,
            enabled = !isBitmap,
            disabledHint = "Can't be customized: PGS / VobSub subtitles are ready-made images, not text"
        )
    }
}

private fun isBitmapSelection(selection: SubtitleSelection, tracks: List<MediaTrack>): Boolean =
    when (selection) {
        is SubtitleSelection.Off -> false
        is SubtitleSelection.Embedded ->
            tracks.firstOrNull { it.index == selection.index }?.isTextBased == false
        is SubtitleSelection.External -> isBitmapSubtitleFile(selection.path)
    }

