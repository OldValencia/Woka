package com.woka.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.rememberWatchMarks
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.FileInfoPanel
import java.io.File

/**
 * Whether the Info panel is open (SIDE-009). For the run, not persisted: it is a
 * thing looked at and put away, and a window that reopened with it would be a
 * window narrower than the user left it for no reason they remember.
 */
object InfoPanel {
    var open by mutableStateOf(false)
}

/**
 * What floats over a folder's grid: the link offer at the top, the selection bar at
 * the bottom, and Quick Look over all of it. Split out of `FolderContent` when the
 * Info panel took its place beside the grid.
 */
@Composable
internal fun BoxScope.FolderFloating(
    path: String,
    state: FolderState,
    viewModel: FolderViewModel,
    focus: FolderFocus,
    showLinkOffer: Boolean,
    onLink: () -> Unit,
    onDismissOffer: () -> Unit
) {
    if (showLinkOffer) {
        FolderPinPrompt(
            folderName = File(path).name,
            onPin = onLink,
            onDismiss = onDismissOffer,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        )
    }
    if (state.isSelectMode) {
        FolderSelectionBar(
            path = path,
            state = state,
            viewModel = viewModel,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
        )
    }
    val looked = state.items.firstOrNull { it.file.absolutePath == focus.focused }
    if (focus.quickLook && looked != null && looked !is MediaItem.Folder) {
        val marks = rememberWatchMarks(viewModel.settingsRepository)
        QuickLook(looked, marks.forVideo(looked), onClose = { focus.quickLook = false })
    }
}

/**
 * The Info panel beside the grid (SIDE-009, `.info` in the mockup): 272.dp, the
 * sidebar's ground, a hairline on its left. It follows the keyboard's focus, so the
 * arrows walk the folder and the panel reads each item as they arrive.
 */
@Composable
internal fun FolderInfoSide(item: MediaItem?) {
    Box(
        modifier = Modifier
            .width(272.dp)
            .fillMaxHeight()
            .background(Theme.colors.surface)
    ) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(Theme.colors.border))
        if (item == null) {
            BasicText(
                "Select an item to see its details",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = TextStyle(color = Theme.colors.textTertiary, fontSize = 12.5.sp)
            )
        } else {
            FileInfoPanel(item, Modifier.fillMaxSize())
        }
    }
}
