package com.woka.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.MediaItem
import com.woka.core.data.WatchRecord
import com.woka.core.ui.components.FolderCardPreview
import com.woka.core.ui.components.StillCoverImage
import com.woka.core.ui.theme.Theme

/**
 * Quick Look (SIDE-005, `.ql` in `sidebar-redesign.html`): the focused item shown
 * whole above the grid, without leaving it. Space opens and closes it, the arrows
 * walk the folder under it, Enter hands over to the viewer, a click on the scrim
 * puts it away.
 *
 * **A video shows its poster, not its picture moving.** Playing it here needs a
 * player of its own and its lifetime, which is its own body of work (backlog,
 * beside SIDE-009); the poster, the duration and the watched bar already say which
 * film this is, and Enter plays it.
 */
@Composable
internal fun QuickLook(item: MediaItem, watch: WatchRecord?, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.74f)
                .widthIn(max = 860.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Theme.colors.surfaceElevated)
                // Clicks on the card stay on the card; only the scrim closes.
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {})
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 2f).heightIn(max = 560.dp)) {
                if (item is MediaItem.Video) {
                    FolderCardPreview(
                        isDir = false,
                        file = item.file,
                        previews = emptyList(),
                        modifier = Modifier.fillMaxSize(),
                        isHovered = true,
                        watch = watch,
                        dimWatched = false
                    )
                } else {
                    StillCoverImage(item.file, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            QuickLookCaption(item.file.name)
        }
    }
}

@Composable
private fun QuickLookCaption(name: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicText(
            name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        BasicText(
            "← → next · Enter open · Space close",
            style = TextStyle(color = Theme.colors.textTertiary, fontSize = 12.sp)
        )
    }
}
