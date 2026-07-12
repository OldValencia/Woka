package io.iris.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import io.iris.data.SortType
import io.iris.data.ViewLayout
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun FolderTopBar(
    isPinned: Boolean,
    isSelectMode: Boolean,
    selectedCount: Int,
    layout: ViewLayout,
    sort: SortType,
    onTogglePin: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onSortChange: (SortType) -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isPinned) IrisTheme.colors.surfaceHover else IrisTheme.colors.surfaceElevated)
                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                .clickable(onClick = onTogglePin)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            BasicText(
                text = if (isPinned) "Pinned" else "Pin",
                style = TextStyle(color = if (isPinned) IrisTheme.colors.accentFolders else IrisTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        if (isSelectMode) {
            BasicText("$selectedCount selected", style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            
            Spacer(modifier = Modifier.width(12.dp))
            
            ActionBox("Select All", true, onSelectAll)
            Spacer(modifier = Modifier.width(12.dp))
            ActionBox("Duplicate", selectedCount > 0, onDuplicate)
            Spacer(modifier = Modifier.width(12.dp))
            ActionBox("Copy", selectedCount > 0, onCopy)
            Spacer(modifier = Modifier.width(12.dp))
            ActionBox("Move", selectedCount > 0, onMove)
            Spacer(modifier = Modifier.width(12.dp))
            ActionBox("Delete", selectedCount > 0, onDelete, isDanger = true)
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelectMode) IrisTheme.colors.surfaceHover else IrisTheme.colors.surfaceElevated)
                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleSelectMode)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            BasicText(
                text = if (isSelectMode) "Done" else "Select",
                style = TextStyle(color = if (isSelectMode) IrisTheme.colors.accentFolders else IrisTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Box {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (showFilterMenu) IrisTheme.colors.surfaceHover else IrisTheme.colors.surfaceElevated)
                    .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                    .clickable { showFilterMenu = !showFilterMenu }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Image(
                    painter = painterResource(IrisIcons.Static.Filter),
                    contentDescription = "Filter",
                    modifier = Modifier.width(21.dp).height(12.dp),
                    colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                )
            }
            
            if (showFilterMenu) {
                Popup(
                    alignment = Alignment.TopEnd,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 36.dp)
                            .width(180.dp)
                            .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                            .background(IrisTheme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                            .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(10.dp))
                            .padding(6.dp)
                    ) {
                        DropdownItem("Zoom In", enabled = layout != ViewLayout.BigGrid) { 
                            onZoomIn()
                            showFilterMenu = false 
                        }
                        DropdownItem("Zoom Out", enabled = layout != ViewLayout.SmallGrid) { 
                            onZoomOut()
                            showFilterMenu = false 
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IrisTheme.colors.border))
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val nameLabel = if (sort == SortType.NameAsc) "Name ↑" else if (sort == SortType.NameDesc) "Name ↓" else "Name"
                        val dateLabel = if (sort == SortType.DateAsc) "Date ↑" else if (sort == SortType.DateDesc) "Date ↓" else "Date"
                        
                        DropdownItem("Sort by $nameLabel") { 
                            onSortChange(if (sort == SortType.NameAsc) SortType.NameDesc else SortType.NameAsc)
                            showFilterMenu = false 
                        }
                        DropdownItem("Sort by $dateLabel") { 
                            onSortChange(if (sort == SortType.DateAsc) SortType.DateDesc else SortType.DateAsc)
                            showFilterMenu = false 
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBox(text: String, enabled: Boolean, onClick: () -> Unit, isDanger: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(IrisTheme.colors.surfaceElevated)
            .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        val color = if (enabled) {
            if (isDanger) IrisTheme.colors.danger else IrisTheme.colors.textPrimary
        } else {
            IrisTheme.colors.textSecondary
        }
        BasicText(text, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}
