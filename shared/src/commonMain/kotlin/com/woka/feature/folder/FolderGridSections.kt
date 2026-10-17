package com.woka.feature.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.MediaItem
import com.woka.core.data.ViewLayout
import com.woka.core.ui.components.gridColumnSize
import com.woka.core.ui.components.tileSize
import com.woka.core.ui.theme.Theme
import kotlin.math.ceil

/**
 * The grid's items: sub-folders under **Folders**, then media under **Media**
 * (SIDE-004). The sections exist whenever the folder has sub-folders
 * (`hasSections`), in both layouts; the indices they shift are mapped back by
 * `gridIndexOf`.
 *
 * In the [mosaic], media are square tiles, and each folder spans as many tile cells
 * as a card is wide at this size, padded so it reads as a card among tiles rather
 * than a tile among tiles. As cards, every item is a card, in one cell.
 */
internal fun LazyGridScope.folderGridItems(
    items: List<MediaItem>,
    mosaic: Boolean,
    layout: ViewLayout,
    itemContent: @Composable (item: MediaItem, tile: Boolean) -> Unit
) {
    val folders = items.filter { it is MediaItem.Folder }
    val media = items.filterNot { it is MediaItem.Folder }
    val sections = items.hasSections()
    val folderSpan = if (mosaic) ceil(layout.gridColumnSize / layout.tileSize).toInt() else 1
    val folderInset = if (mosaic) 10.dp else 0.dp

    if (sections) heading("Folders", folders.size)
    items(
        folders,
        key = { it.file.absolutePath },
        span = { GridItemSpan(minOf(folderSpan, maxLineSpan)) }
    ) {
        Box(modifier = Modifier.padding(folderInset)) { itemContent(it, false) }
    }
    if (sections) heading("Media", media.size)
    items(media, key = { it.file.absolutePath }) { itemContent(it, mosaic) }
}

private fun LazyGridScope.heading(title: String, count: Int) {
    item(key = "heading-$title", span = { GridItemSpan(maxLineSpan) }) {
        BasicText(
            "$title  $count",
            modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 8.dp),
            style = TextStyle(
                color = Theme.colors.textTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
