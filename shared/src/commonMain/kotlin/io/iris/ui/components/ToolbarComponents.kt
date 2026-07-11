package io.iris.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
fun LibraryHeader(
    title: String,
    subtitle: String,
    currentSort: SortType,
    onSortChange: (SortType) -> Unit,
    currentLayout: ViewLayout,
    onLayoutChange: (ViewLayout) -> Unit,
    showTitles: Boolean,
    onShowTitlesChange: (Boolean) -> Unit,
    mediaFilter: String,
    onMediaFilterChange: (String) -> Unit,
    showMediaFilter: Boolean,
    onSelectClick: () -> Unit,
    modifier: Modifier = Modifier,
    isScrolled: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    breadcrumbs: List<Pair<String, () -> Unit>> = emptyList()
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isScrolled) IrisTheme.colors.background.copy(alpha = 0.85f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            var titleExpanded by remember { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBackClick != null) {
                    Image(
                        painter = painterResource(IrisIcons.Static.ChevronRight),
                        contentDescription = "Back",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(180f)
                            .clip(RoundedCornerShape(4.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onBackClick() },
                        colorFilter = ColorFilter.tint(IrisTheme.colors.accent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = breadcrumbs.isNotEmpty()) { titleExpanded = true }
                            .pointerHoverIcon(if (breadcrumbs.isNotEmpty()) PointerIcon.Hand else PointerIcon.Default)
                    ) {
                        BasicText(
                            text = title,
                            style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        )
                        if (breadcrumbs.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Image(
                                painter = painterResource(IrisIcons.Static.ChevronRight),
                                contentDescription = "Path",
                                modifier = Modifier.size(16.dp).rotate(90f),
                                colorFilter = ColorFilter.tint(IrisTheme.colors.textSecondary)
                            )
                        }
                    }
                    
                    if (breadcrumbs.isNotEmpty()) {
                        DropdownMenu(
                            expanded = titleExpanded,
                            onDismissRequest = { titleExpanded = false },
                            modifier = Modifier.background(IrisTheme.colors.surface)
                        ) {
                            breadcrumbs.reversed().forEachIndexed { index, node ->
                                DropdownMenuItem(
                                    onClick = { 
                                        titleExpanded = false
                                        node.second() 
                                    },
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (node.first == "Home") {
                                                Image(
                                                    painter = painterResource(IrisIcons.Static.Home),
                                                    contentDescription = "Home",
                                                    modifier = Modifier.size(14.dp),
                                                    colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                                                )
                                            } else {
                                                Image(
                                                    painter = painterResource(IrisIcons.Static.Folder),
                                                    contentDescription = "Folder",
                                                    modifier = Modifier.size(14.dp),
                                                    colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BasicText(node.first, style = TextStyle(color = IrisTheme.colors.textPrimary, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 12.sp)
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(IrisTheme.colors.surface)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = true }
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(IrisIcons.Static.Filter),
                contentDescription = "Options",
                modifier = Modifier.size(20.dp).scale(scaleX = 1f, scaleY = 0.6f),
                colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(IrisTheme.colors.surface)
            ) {
                val isDateAsc = currentSort == SortType.DateAsc
                val isDateDesc = currentSort == SortType.DateDesc
                val isNameAsc = currentSort == SortType.NameAsc
                val isNameDesc = currentSort == SortType.NameDesc

                DropdownMenuItem(
                    onClick = { 
                        if (isDateDesc) onSortChange(SortType.DateAsc)
                        else onSortChange(SortType.DateDesc)
                        expanded = false 
                    },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText("Sort by date modified", style = TextStyle(
                                color = if (isDateDesc || isDateAsc) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary
                            ))
                            if (isDateDesc || isDateAsc) {
                                Spacer(Modifier.width(8.dp))
                                Image(
                                    painter = painterResource(IrisIcons.Static.ChevronRight),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp).rotate(if (isDateAsc) -90f else 90f),
                                    colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                                )
                            }
                        }
                    }
                )
                DropdownMenuItem(
                    onClick = { 
                        if (isNameAsc) onSortChange(SortType.NameDesc)
                        else onSortChange(SortType.NameAsc)
                        expanded = false 
                    },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText("Sort by name", style = TextStyle(
                                color = if (isNameAsc || isNameDesc) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary
                            ))
                            if (isNameAsc || isNameDesc) {
                                Spacer(Modifier.width(8.dp))
                                Image(
                                    painter = painterResource(IrisIcons.Static.ChevronRight),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp).rotate(if (isNameAsc) -90f else 90f),
                                    colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                                )
                            }
                        }
                    }
                )

                HorizontalDivider(color = IrisTheme.colors.border)
                
                if (showMediaFilter) {
                    DropdownMenuItem(
                        onClick = { onMediaFilterChange("All"); expanded = false },
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(painter = painterResource(IrisIcons.Static.Image), contentDescription = null, modifier = Modifier.size(14.dp), colorFilter = ColorFilter.tint(if (mediaFilter == "All") IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary))
                                Spacer(Modifier.width(2.dp))
                                Image(painter = painterResource(IrisIcons.Static.Video), contentDescription = null, modifier = Modifier.size(14.dp), colorFilter = ColorFilter.tint(if (mediaFilter == "All") IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary))
                                Spacer(Modifier.width(8.dp))
                                BasicText("All media", style = TextStyle(color = if (mediaFilter == "All") IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary))
                            }
                        }
                    )
                    DropdownMenuItem(
                        onClick = { onMediaFilterChange("Photos"); expanded = false },
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(painter = painterResource(IrisIcons.Static.Image), contentDescription = null, modifier = Modifier.size(14.dp), colorFilter = ColorFilter.tint(if (mediaFilter == "Photos") IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary))
                                Spacer(Modifier.width(8.dp))
                                BasicText("Photos", style = TextStyle(color = if (mediaFilter == "Photos") IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary))
                            }
                        }
                    )
                    DropdownMenuItem(
                        onClick = { onMediaFilterChange("Videos"); expanded = false },
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(painter = painterResource(IrisIcons.Static.Video), contentDescription = null, modifier = Modifier.size(14.dp), colorFilter = ColorFilter.tint(if (mediaFilter == "Videos") IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary))
                                Spacer(Modifier.width(8.dp))
                                BasicText("Videos", style = TextStyle(color = if (mediaFilter == "Videos") IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary))
                            }
                        }
                    )
                    HorizontalDivider(color = IrisTheme.colors.border)
                }

                DropdownMenuItem(
                    onClick = { 
                        if (currentLayout != ViewLayout.BigGrid) {
                            val newLayout = when (currentLayout) {
                                ViewLayout.SmallGrid -> ViewLayout.Grid
                                else -> ViewLayout.BigGrid
                            }
                            onLayoutChange(newLayout) 
                        }
                    },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isEnabled = currentLayout != ViewLayout.BigGrid
                            Image(
                                painter = painterResource(IrisIcons.Static.MagnifyingGlassPlus),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                colorFilter = ColorFilter.tint(if (isEnabled) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary.copy(alpha=0.5f))
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicText("Zoom in", style = TextStyle(color = if (isEnabled) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary.copy(alpha=0.5f)))
                        }
                    }
                )
                DropdownMenuItem(
                    onClick = { 
                        if (currentLayout != ViewLayout.SmallGrid) {
                            val newLayout = when (currentLayout) {
                                ViewLayout.BigGrid -> ViewLayout.Grid
                                else -> ViewLayout.SmallGrid
                            }
                            onLayoutChange(newLayout) 
                        }
                    },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isEnabled = currentLayout != ViewLayout.SmallGrid
                            Image(
                                painter = painterResource(IrisIcons.Static.MagnifyingGlassPlus),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                colorFilter = ColorFilter.tint(if (isEnabled) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary.copy(alpha=0.5f))
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicText("Zoom out", style = TextStyle(color = if (isEnabled) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary.copy(alpha=0.5f)))
                        }
                    }
                )

                HorizontalDivider(color = IrisTheme.colors.border)

                DropdownMenuItem(
                    onClick = { onShowTitlesChange(!showTitles); expanded = false },
                    text = { BasicText(if (showTitles) "Hide titles" else "Show titles", style = TextStyle(color = IrisTheme.colors.textPrimary)) }
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(IrisTheme.colors.surface)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { onSelectClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText("Select", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 14.sp))
        }
    }
}

@Composable
fun BottomAppMenu(
    onSettingsClick: () -> Unit,
    showLinkFolder: Boolean,
    onLinkFolderClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IrisTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { onSettingsClick() }
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(IrisIcons.Static.Settings),
                contentDescription = "Settings",
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(IrisTheme.colors.textSecondary)
            )
        }
        
        if (showLinkFolder) {
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onLinkFolderClick() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("+ ", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    BasicText("Link Folder", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 14.sp))
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(IrisTheme.colors.background)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .width(200.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(IrisIcons.Static.Search),
                contentDescription = "Search",
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(IrisTheme.colors.textSecondary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                textStyle = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 14.sp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        BasicText("Search...", style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 14.sp))
                    }
                    innerTextField()
                }
            )
        }
    }
}
