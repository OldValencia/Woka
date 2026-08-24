package com.woka.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.data.WatchRecord
import com.woka.core.ui.theme.Theme

/**
 * What a card looks like: the 16:10 cover, everything it says written **on** it,
 * and — for a folder — the edge of the stack peeking out beneath.
 *
 * The title row that used to sit under the tile is gone; [FolderCardCaption] took
 * its job onto the cover, which is what made the card 34 dp shorter and stopped the
 * word "Folder" repeating down a screen made of folders.
 */
@Composable
internal fun FolderCardBody(
    data: FolderCardData,
    style: FolderCardStyle,
    showTitle: Boolean,
    isHovered: Boolean,
    onRemove: (() -> Unit)?,
    watch: WatchRecord?,
    dimWatched: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .shadow(
                    elevation = style.shadowElev,
                    shape = RoundedCornerShape(10.dp),
                    spotColor = style.glowColor,
                    ambientColor = style.ambientColor
                )
                .background(Theme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                .border(style.borderWidth, style.borderColor, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            FolderCardPreview(
                isDir = data.isDir,
                file = data.file,
                previews = data.previews,
                modifier = Modifier.fillMaxSize(),
                isHovered = isHovered,
                watch = watch,
                dimWatched = dimWatched
            )

            if (showTitle) {
                FolderCardCaption(
                    name = data.name,
                    meta = data.meta,
                    isHovered = isHovered
                )
            }

            if (onRemove != null && isHovered) {
                FolderCardRemoveButton(
                    onRemove = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        // What the mosaic used to say, said by the shape of the card instead: two
        // sheets of paper under the top one. Folders only — a photograph has
        // nothing behind it.
        if (data.isDir) {
            StackEdge(inset = 8.dp, color = Theme.colors.surfaceElevated)
            StackEdge(inset = 15.dp, color = Theme.colors.surfaceElevated.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun StackEdge(inset: Dp, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(4.dp)
            .background(color, RoundedCornerShape(bottomStart = 9.dp, bottomEnd = 9.dp))
    )
}
