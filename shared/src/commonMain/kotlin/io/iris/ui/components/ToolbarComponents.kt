package io.iris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.data.SortType
import io.iris.data.ViewLayout
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun ViewLayoutSwitcher(
    currentLayout: ViewLayout,
    onLayoutChange: (ViewLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(IrisTheme.colors.surface)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(IrisIcons.Static.Grid),
                contentDescription = "Grid Size",
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
            )
            BasicText(
                text = "Grid Size",
                style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 14.sp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(IrisTheme.colors.surface)
        ) {
            ViewLayout.entries.forEach { layout ->
                val (layoutName, iconSize) = when (layout) {
                    ViewLayout.SmallGrid -> "Small" to 12.dp
                    ViewLayout.Grid -> "Medium" to 16.dp
                    ViewLayout.BigGrid -> "Large" to 20.dp
                }
                
                DropdownMenuItem(
                    onClick = {
                        onLayoutChange(layout)
                        expanded = false
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(IrisIcons.Static.Grid),
                                contentDescription = null,
                                modifier = Modifier.size(iconSize),
                                colorFilter = ColorFilter.tint(
                                    if (layout == currentLayout) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            BasicText(
                                text = layoutName,
                                style = TextStyle(
                                    color = if (layout == currentLayout) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (layout == currentLayout) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

fun SortType.toUserFriendlyName(): String = when (this) {
    SortType.Custom -> "Custom Order"
    SortType.NameAsc -> "Name (A to Z)"
    SortType.NameDesc -> "Name (Z to A)"
    SortType.DateAsc -> "Date (Oldest first)"
    SortType.DateDesc -> "Date (Newest first)"
}

@Composable
fun SortSwitcher(
    currentSort: SortType,
    onSortChange: (SortType) -> Unit,
    modifier: Modifier = Modifier,
    allowCustomOrder: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(IrisTheme.colors.surface)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(IrisIcons.Static.Sort),
                contentDescription = "Sort",
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
            )
            BasicText(
                text = currentSort.toUserFriendlyName(),
                style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 14.sp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(IrisTheme.colors.surface)
        ) {
            val options = if (allowCustomOrder) SortType.entries else SortType.entries.filter { it != SortType.Custom }
            options.forEach { sort ->
                DropdownMenuItem(
                    onClick = {
                        onSortChange(sort)
                        expanded = false
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(IrisIcons.Static.Sort),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).let { 
                                    if (sort == SortType.NameDesc || sort == SortType.DateDesc) it.rotate(180f) else it 
                                },
                                colorFilter = ColorFilter.tint(
                                    if (sort == currentSort) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            BasicText(
                                text = sort.toUserFriendlyName(),
                                style = TextStyle(
                                    color = if (sort == currentSort) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (sort == currentSort) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}
