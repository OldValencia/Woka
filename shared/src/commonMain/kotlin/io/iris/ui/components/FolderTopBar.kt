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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import io.iris.data.SortType
import io.iris.data.ViewLayout
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LibraryLeftAccessory(
    isPinned: Boolean,
    isSelectMode: Boolean,
    layout: ViewLayout,
    sort: SortType,
    onTogglePin: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onSortChange: (SortType) -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .padding(end = 12.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black.copy(alpha = 0.5f),
                ambientColor = Color.Black.copy(alpha = 0.5f)
            )
            .background(IrisTheme.colors.surface, RoundedCornerShape(20.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccessoryPill(
            text = if (isPinned) "Pinned" else "Pin",
            isActive = isPinned,
            onClick = onTogglePin
        )
        
        AccessoryPill(
            text = if (isSelectMode) "Done" else "Select",
            isActive = isSelectMode,
            onClick = onToggleSelectMode
        )

        Box {
            val scope = rememberCoroutineScope()
            var isDismissing by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (showFilterMenu) IrisTheme.colors.surfaceHover else Color.Transparent)
                    .clickable { 
                        if (!isDismissing) {
                            showFilterMenu = !showFilterMenu 
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Image(
                    painter = painterResource(IrisIcons.Static.Filter),
                    contentDescription = "Filter",
                    modifier = Modifier.width(21.dp).height(12.dp),
                    colorFilter = ColorFilter.tint(if (showFilterMenu) IrisTheme.colors.accentFolders else IrisTheme.colors.textPrimary)
                )
            }
            
            if (showFilterMenu) {
                Popup(
                    alignment = Alignment.TopCenter,
                    offset = IntOffset(0, -165),
                    onDismissRequest = { 
                        showFilterMenu = false 
                        isDismissing = true
                        scope.launch { delay(100.milliseconds); isDismissing = false }
                    }
                ) {
                    Column(
                        modifier = Modifier
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
fun LibraryTopAccessory(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color.Black.copy(alpha = 0.5f),
                ambientColor = Color.Black.copy(alpha = 0.5f)
            )
            .background(IrisTheme.colors.surface, RoundedCornerShape(20.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "$selectedCount selected",
            style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        Box(modifier = Modifier.width(1.dp).height(20.dp).background(IrisTheme.colors.border))
        
        AccessoryPill("Select All", enabled = true, onClick = onSelectAll)
        AccessoryPill("Duplicate", enabled = selectedCount > 0, onClick = onDuplicate)
        AccessoryPill("Copy", enabled = selectedCount > 0, onClick = onCopy)
        AccessoryPill("Move", enabled = selectedCount > 0, onClick = onMove)
        AccessoryPill("Delete", enabled = selectedCount > 0, onClick = onDelete, isDanger = true)
    }
}

@Composable
private fun AccessoryPill(text: String, isActive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit, isDanger: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) IrisTheme.colors.surfaceHover else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        val color = if (!enabled) IrisTheme.colors.textSecondary else if (isDanger) IrisTheme.colors.danger else if (isActive) IrisTheme.colors.accentFolders else IrisTheme.colors.textPrimary
        BasicText(text, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}
