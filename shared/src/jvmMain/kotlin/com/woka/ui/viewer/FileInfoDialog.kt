package com.woka.ui.viewer

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.data.MediaItem
import com.woka.ui.components.dialogs.AbstractDialog
import com.woka.ui.components.dialogs.DialogButton
import com.woka.ui.components.dialogs.DialogTitle
import com.woka.ui.theme.Theme
import com.woka.ui.viewer.player.MediaTracks
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun FileInfoDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
    mediaTracks: MediaTracks? = null,
    durationMs: Long? = null,
    selectedAudioIndex: Int? = null
) {
    AbstractDialog(onDismissRequest = onDismiss, width = if (mediaTracks != null) 340.dp else 300.dp) {
        DialogTitle(text = "File Information", color = Theme.colors.textPrimary)
        if (mediaTracks == null) {
            FileMetaBlock(item)
        } else {
            ScrollableInfo(item, mediaTracks, durationMs, selectedAudioIndex)
        }
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogButton(
                text = "Copy",
                onClick = { copyToClipboard(buildInfoText(item, mediaTracks, durationMs)) },
                color = Theme.colors.textSecondary
            )
            DialogButton(text = "Close", onClick = onDismiss, color = Theme.colors.accent)
        }
    }
}

private fun copyToClipboard(text: String) {
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
}

@Composable
private fun ScrollableInfo(
    item: MediaItem,
    mediaTracks: MediaTracks,
    durationMs: Long?,
    selectedAudioIndex: Int?
) {
    val scrollState = rememberScrollState()
    // The outer Box hugs the (height-capped) content column; the scrollbar sits
    // in a matchParentSize overlay so its fillMaxHeight matches the content
    // instead of blowing the dialog up to the full window height.
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(scrollState)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FileMetaBlock(item)
            StreamInfoSections(mediaTracks, durationMs, selectedAudioIndex)
        }
        Box(modifier = Modifier.matchParentSize()) {
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun FileMetaBlock(item: MediaItem) {
    SelectionContainer(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            InfoLine("Name: ${item.file.name}")
            InfoLine("Path: ${item.file.absolutePath}")
            InfoLine("Size: ${item.file.length().toReadableSize()}")
            InfoLine("Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(item.file.lastModified()))}")
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        style = TextStyle(color = Theme.colors.textSecondary, fontSize = 13.sp)
    )
}
