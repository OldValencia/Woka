package com.woka.feature.viewer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.FolderCardPreview
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Info panel's content (SIDE-009, `.info` in `sidebar-redesign.html`).
 *
 * Laid out for itself rather than borrowed from the File Information dialog: the
 * first cut reused the dialog's grey lines and, from the running app, read as a
 * draft (2026-10-17). Now the picture leads, the name follows in the strong weight,
 * the folder under it, and everything else is label-and-value in titled groups —
 * labels quiet, values in full colour, so the eye finds the values.
 */
@Composable
actual fun FileInfoPanel(item: MediaItem, modifier: Modifier) {
    val probed by produceState<Probed?>(null, item.file) {
        value = withContext(Dispatchers.IO) { probe(item) }
    }
    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(10.dp))
            ) {
                FolderCardPreview(item is MediaItem.Folder, item.file, emptyList(), Modifier.fillMaxSize())
            }
            BasicText(
                item.file.name,
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            BasicText(
                item.file.parentFile?.name.orEmpty(),
                style = TextStyle(color = Theme.colors.textTertiary, fontSize = 11.5.sp)
            )
            panelSections(item, probed).forEach { section -> Section(section) }
        }
    }
}

@Composable
private fun Section(section: InfoSection) {
    BasicText(
        section.title.uppercase(),
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        style = TextStyle(
            color = Theme.colors.accent,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    )
    section.rows.forEach { (label, value) ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            BasicText(
                label,
                modifier = Modifier.width(78.dp),
                style = TextStyle(color = Theme.colors.textTertiary, fontSize = 12.sp)
            )
            BasicText(
                value,
                style = TextStyle(color = Theme.colors.textPrimary, fontSize = 12.sp)
            )
        }
    }
}
