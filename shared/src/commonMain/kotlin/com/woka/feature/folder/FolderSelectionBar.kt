package com.woka.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.AccessoryPill
import com.woka.core.ui.components.floatingPanel
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.openDirectoryPicker

/**
 * `.selbar` (SIDE-003): the only thing that floats over the grid, and only while the
 * folder is in select mode. What the dock's top accessory carried, plus the count
 * and a **Done** that ends the mode — the dock had a Select/Done pill for that, and
 * with the dock gone the bar is where leaving has to live.
 *
 * Every action ends select mode, as the dock's did: a selection acted on is spent.
 */
@Composable
internal fun FolderSelectionBar(
    path: String,
    state: FolderState,
    viewModel: FolderViewModel,
    modifier: Modifier = Modifier
) {
    val selected = state.selectedPaths.toList()
    val any = selected.isNotEmpty()
    val finish = { viewModel.toggleSelectMode() }
    Row(
        modifier = modifier.floatingPanel(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "${selected.size} selected",
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Divider()
        AccessoryPill("Select All", onClick = { viewModel.selectAll() }, isSmall = true)
        AccessoryPill("Copy…", enabled = any, isSmall = true, onClick = {
            val dest = openDirectoryPicker("Copy to...", path)
            if (dest != null) {
                viewModel.copyFiles(selected, dest)
                finish()
            }
        })
        AccessoryPill("Move…", enabled = any, isSmall = true, onClick = {
            val dest = openDirectoryPicker("Move to...", path)
            if (dest != null) {
                viewModel.moveFiles(selected, dest)
                finish()
            }
        })
        AccessoryPill("Duplicate", enabled = any, isSmall = true, onClick = {
            viewModel.duplicateFiles(selected)
            finish()
        })
        Divider()
        AccessoryPill("Delete", enabled = any, isDanger = true, isSmall = true, onClick = {
            viewModel.deleteFiles(selected)
            finish()
        })
        Divider()
        AccessoryPill("Done", isActive = true, isSmall = true, onClick = finish)
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.width(1.dp).height(18.dp).background(Theme.colors.border))
}
