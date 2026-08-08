package com.woka.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource
import java.io.File

/**
 * The cover of a folder card: up to three previews arranged as one, two, or a
 * one-plus-two mosaic, and the folder glyph when there is nothing to show.
 *
 * All the tiles were seven copies of the same `AsyncImage` differing only in
 * modifier (§11), so the picture is [PreviewTile] plus three arrangements.
 */
@Composable
internal fun FolderCoverMosaic(previews: List<File>) {
    when {
        previews.isEmpty() -> Image(
            painter = painterResource(Icons.Static.Folder),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            alpha = 0.5f,
            colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
        )

        previews.size == 1 -> PreviewTile(previews[0], Modifier.fillMaxSize())

        previews.size == 2 -> MosaicRow {
            PreviewTile(previews[0], Modifier.weight(1f).fillMaxHeight())
            PreviewTile(previews[1], Modifier.weight(1f).fillMaxHeight())
        }

        else -> MosaicRow {
            PreviewTile(previews[0], Modifier.weight(1f).fillMaxHeight())
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                PreviewTile(previews[1], Modifier.weight(1f).fillMaxWidth())
                PreviewTile(previews[2], Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MosaicRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        content = content
    )
}

@Composable
private fun PreviewTile(file: File, modifier: Modifier) {
    StillCoverImage(file, modifier)
}
