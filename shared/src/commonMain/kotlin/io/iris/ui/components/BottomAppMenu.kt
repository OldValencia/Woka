package io.iris.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun BottomAppMenu(
    onSettingsClick: () -> Unit,
    showLinkFolder: Boolean,
    onLinkFolderClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    var isSearchFocused by remember { mutableStateOf(false) }

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
                .border(
                    width = 1.dp,
                    color = if (isSearchFocused) IrisTheme.colors.accent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
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
                modifier = Modifier.fillMaxWidth().onFocusChanged { isSearchFocused = it.isFocused },
                cursorBrush = SolidColor(IrisTheme.colors.accent),
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
