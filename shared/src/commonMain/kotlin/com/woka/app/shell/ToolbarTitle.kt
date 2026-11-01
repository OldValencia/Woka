package com.woka.app.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.app.AppDependencies
import com.woka.app.collectField
import com.woka.app.navigation.Screen
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.folder.parentWithinRoot
import java.io.File

/**
 * `‹` before the title: up one folder, the same step Backspace takes in the grid
 * (`parentWithinRoot`). Absent at a linked root and outside every root.
 *
 * **Up, not history** (2026-11-01). History Back was removed on 2026-10-17 and stays
 * removed — the author: "where I was before" reads as unintuitive. Up is the step a
 * deep folder was missing on screen; until now only the keyboard knew it.
 */
@Composable
fun FolderUpButton(path: String) {
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val linked by settingsRepo.collectField { it.linkedFolders }
    val parent = remember(path, linked) { parentWithinRoot(path, linked) } ?: return
    val name = remember(parent) { File(parent).name.ifEmpty { parent } }
    ToolbarIconButton(Icons.Static.ChevronLeft, "Back to $name", onClick = {
        AppDependencies.router.navigateTo(Screen.Folder(parent))
    })
}

/**
 * The open folder's name, as the toolbar's title (`.title h2`): one line, cut with
 * an ellipsis when the controls beside it need the room. A drive root has no name
 * of its own, so it shows its path.
 */
@Composable
fun ToolbarTitle(path: String) {
    val name = remember(path) { File(path).name.ifEmpty { path } }
    BasicText(
        name,
        modifier = Modifier.padding(start = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = Theme.colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
}
