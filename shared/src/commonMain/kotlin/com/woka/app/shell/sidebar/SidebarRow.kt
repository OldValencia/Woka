package com.woka.app.shell.sidebar

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import com.woka.core.data.PinnedSession
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource
import java.io.File

/** What a tree row shows, so the row takes one value instead of five flags. */
@Immutable
internal data class FolderRowModel(
    val node: SidebarNode,
    val depth: Int,
    val expandable: Boolean,
    val expanded: Boolean,
    val selected: Boolean
)

/**
 * One folder in the tree: a chevron when it has sub-folders, the folder glyph, the
 * name and the media directly inside it. The chevron is drawn but invisible on a
 * leaf, so names at one depth line up whether or not they can open.
 */
@Composable
internal fun SidebarFolderRow(
    model: FolderRowModel,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onSecondaryClick: (IntOffset) -> Unit
) {
    SidebarRowFrame(model.selected, SidebarTokens.RowHeight, true, onClick, onSecondaryClick) {
        Image(
            painter = painterResource(Icons.Static.ChevronRightMac),
            contentDescription = if (model.expanded) "Collapse" else "Expand",
            modifier = Modifier
                .padding(start = SidebarTokens.Indent * model.depth)
                .size(SidebarTokens.ChevronSize)
                .rotate(if (model.expanded) 90f else 0f)
                .clickable(enabled = model.expandable, onClick = onToggle),
            colorFilter = ColorFilter.tint(
                if (model.expandable) Theme.colors.textTertiary else Color.Transparent
            )
        )
        RowIcon(model.selected, Theme.colors.accentFolders)
        RowLabel(model.node.name, Modifier.weight(1f), Theme.colors.textPrimary)
        if (model.node.mediaCount > 0) {
            BasicText(
                model.node.mediaCount.toString(),
                style = TextStyle(color = Theme.colors.textTertiary, fontSize = SidebarTokens.SubSize)
            )
        }
    }
}

/**
 * A pinned folder: its name over [label], the place it sits. A pin whose folder has
 * gone stays listed so it can be unpinned, but greyed and deaf to clicks.
 */
@Composable
internal fun SidebarPinnedRow(
    session: PinnedSession,
    label: String,
    selected: Boolean,
    exists: Boolean,
    onClick: () -> Unit,
    onSecondaryClick: (IntOffset) -> Unit
) {
    SidebarRowFrame(selected, SidebarTokens.PinnedRowHeight, exists, onClick, onSecondaryClick) {
        RowIcon(selected, Theme.colors.textSecondary, Icons.Static.Pin)
        Column(modifier = Modifier.weight(1f)) {
            val tone = if (exists) Theme.colors.textPrimary else Theme.colors.textTertiary
            RowLabel(File(session.path).name, Modifier, tone)
            RowLabel(label, Modifier, Theme.colors.textTertiary, SidebarTokens.SubSize)
        }
    }
}
