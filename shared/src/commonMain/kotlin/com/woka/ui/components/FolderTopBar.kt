package com.woka.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.woka.data.SortType
import com.woka.data.ViewLayout
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
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
            .background(Theme.colors.surface.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
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
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (showFilterMenu) Theme.colors.surfaceHover else Color.Transparent)
                    .clickable {
                        if (!isDismissing) {
                            showFilterMenu = !showFilterMenu
                        }
                    }
                    .padding(horizontal = 14.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Icons.Static.Filter),
                    contentDescription = "Filter",
                    modifier = Modifier.width(21.dp).height(12.dp),
                    colorFilter = ColorFilter.tint(if (showFilterMenu) Theme.colors.accentFolders else Theme.colors.textPrimary)
                )
            }

            var popupState by remember { mutableStateOf(false) }
            LaunchedEffect(showFilterMenu) {
                if (showFilterMenu) popupState = true
                else {
                    delay(200.milliseconds)
                    popupState = false
                }
            }

            if (popupState) {
                LibraryFilterMenu(
                    showFilterMenu = showFilterMenu,
                    onDismissRequest = {
                        showFilterMenu = false
                        isDismissing = true
                        scope.launch { delay(100.milliseconds); isDismissing = false }
                    },
                    layout = layout,
                    sort = sort,
                    onZoomIn = onZoomIn,
                    onZoomOut = onZoomOut,
                    onSortChange = onSortChange,
                    onCloseMenu = { showFilterMenu = false }
                )
            }
        }
    }
}

