package com.woka.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.woka.data.SortType
import com.woka.data.ViewLayout
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme

@Composable
fun LibraryFilterMenu(
    showFilterMenu: Boolean,
    onDismissRequest: () -> Unit,
    layout: ViewLayout,
    sort: SortType,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onSortChange: (SortType) -> Unit,
    onCloseMenu: () -> Unit
) {
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, -165),
        onDismissRequest = onDismissRequest
    ) {
        AnimatedVisibility(
            visible = showFilterMenu,
            enter = expandVertically(animationSpec = tween(150, easing = FastOutSlowInEasing)) + fadeIn(
                animationSpec = tween(150)
            ),
            exit = shrinkVertically(animationSpec = tween(150, easing = FastOutSlowInEasing)) + fadeOut(
                animationSpec = tween(150)
            )
        ) {
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                    .background(Theme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(10.dp))
                    .padding(6.dp)
            ) {
                DropdownItem(
                    "Zoom In",
                    enabled = layout != ViewLayout.BigGrid,
                    iconRes = Icons.Static.MagnifyingGlassPlus
                ) {
                    onZoomIn()
                    onCloseMenu()
                }
                DropdownItem(
                    "Zoom Out",
                    enabled = layout != ViewLayout.SmallGrid,
                    iconRes = Icons.Static.ZoomOut
                ) {
                    onZoomOut()
                    onCloseMenu()
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
                Spacer(modifier = Modifier.height(4.dp))

                val nameLabel =
                    if (sort == SortType.NameAsc) "Name ↑" else if (sort == SortType.NameDesc) "Name ↓" else "Name"
                val dateLabel =
                    if (sort == SortType.DateAsc) "Date ↑" else if (sort == SortType.DateDesc) "Date ↓" else "Date"

                DropdownItem("Sort by $nameLabel", iconRes = Icons.Static.Sort) {
                    onSortChange(if (sort == SortType.NameAsc) SortType.NameDesc else SortType.NameAsc)
                    onCloseMenu()
                }
                DropdownItem("Sort by $dateLabel", iconRes = Icons.Static.Sort) {
                    onSortChange(if (sort == SortType.DateAsc) SortType.DateDesc else SortType.DateAsc)
                    onCloseMenu()
                }
            }
        }
    }
}
