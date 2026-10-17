package com.woka.feature.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.app.AppDependencies
import com.woka.app.shell.ToolbarIconButton
import com.woka.core.data.SortType
import com.woka.core.data.ViewLayout
import com.woka.core.ui.components.SegmentedControl
import com.woka.core.ui.components.SegmentedControlColors
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme

private val FILTERS = listOf("All", "Photos", "Videos")
private val SIZES = listOf("S" to ViewLayout.SmallGrid, "M" to ViewLayout.Grid, "L" to ViewLayout.BigGrid)
private val SORTS = listOf("Name", "Date")

/** Every control is this wide, so the three rows line up into one column. */
private val CONTROL_WIDTH = 240.dp
private val LABEL_WIDTH = 52.dp

/** The panel's width: both columns, the row inset and `MenuSurface`'s own padding. */
internal val VIEW_OPTIONS_WIDTH = 330.dp

/**
 * What the toolbar's **View** button opens: what the folder shows, how big, and in
 * what order.
 *
 * **Size is S · M · L, one for every folder** (2026-10-17). It was a three-detent
 * slider per folder, and in use it was neither: the middle detent could not be
 * reached once left, and each folder not yet sized opened at M whatever the last one
 * used. Three named steps say exactly what there is; one setting follows the user.
 *
 * Sort is the field and its direction, set apart. Picking the field already chosen
 * flips the direction, as the arrow beside it does.
 *
 * **The segments are filled with the accent** (2026-10-17). In the shared default the
 * track is 3 % white and the idle labels `textSecondary`, which on a menu surface left
 * "Photos" and "Videos" barely legible and the pill hard to find at all. Here the
 * control is the whole panel, so it is drawn taller, larger and in colour.
 */
@Composable
internal fun FolderViewOptions(path: String, state: FolderState) {
    val settingsRepo = remember { AppDependencies.settingsRepository }
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OptionRow("Show") {
            Segments(FILTERS, state.mediaFilter, onSelect = { settingsRepo.setMediaFilter(it) })
        }
        OptionRow("Size") {
            val selected = SIZES.firstOrNull { it.second == state.layout }?.first ?: "M"
            Segments(SIZES.map { it.first }, selected, onSelect = { label ->
                SIZES.firstOrNull { it.first == label }?.let { settingsRepo.setGridLayout(it.second) }
            })
        }
        OptionRow("Sort") {
            val byDate = state.sort == SortType.DateAsc || state.sort == SortType.DateDesc
            val descending = state.sort == SortType.NameDesc || state.sort == SortType.DateDesc
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.width(CONTROL_WIDTH - 36.dp)) {
                    Segments(SORTS, if (byDate) "Date" else "Name", onSelect = { field ->
                        settingsRepo.setFolderSort(path, sortOf(field == "Date", descending))
                    })
                }
                ToolbarIconButton(
                    icon = Icons.Static.Sort,
                    description = if (descending) "Descending" else "Ascending",
                    onClick = { settingsRepo.setFolderSort(path, sortOf(byDate, !descending)) },
                    active = descending
                )
            }
        }
    }
}

private fun sortOf(byDate: Boolean, descending: Boolean): SortType = when {
    byDate && descending -> SortType.DateDesc
    byDate -> SortType.DateAsc
    descending -> SortType.NameDesc
    else -> SortType.NameAsc
}

@Composable
private fun OptionRow(label: String, control: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            label,
            modifier = Modifier.width(LABEL_WIDTH),
            style = TextStyle(
                color = Theme.colors.textTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Box(modifier = Modifier.width(CONTROL_WIDTH)) { control() }
    }
}

@Composable
private fun Segments(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    SegmentedControl(
        options = options,
        selected = selected,
        onSelect = onSelect,
        colors = SegmentedControlColors(
            track = Theme.colors.background,
            indicator = Theme.colors.accent,
            indicatorBorder = Theme.colors.accent,
            selectedText = Theme.colors.onAccent,
            text = Theme.colors.textTertiary
        ),
        height = 30.dp,
        textSize = 13.sp
    )
}
