package com.woka.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.draw.shadow
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

@Composable
fun FolderCardBadge(
    isDir: Boolean,
    dynamicBadgeText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .background(Theme.colors.background.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
            .border(1.dp, Theme.colors.border, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isDir) {
                Image(
                    painter = painterResource(Icons.Static.Folder),
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
                )
            }
            BasicText(
                text = dynamicBadgeText,
                style = TextStyle(color = Theme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun FolderCardRemoveButton(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .width(38.dp)
            .height(24.dp)
            .background(Theme.colors.background.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            .border(1.dp, Theme.colors.border, RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onRemove() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Icons.Static.Close),
            contentDescription = "Remove",
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
        )
    }
}

@Composable
fun FolderCardTitle(
    name: String,
    subtitleText: String,
    titleColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 2.dp)) {
        BasicText(
            text = name,
            style = TextStyle(color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = subtitleText,
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 12.sp),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun FolderCardContextMenu(
    offset: IntOffset,
    onDismiss: () -> Unit,
    content: @Composable (dismissMenu: () -> Unit) -> Unit
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .width(180.dp)
                .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                .background(Theme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                .border(1.dp, Theme.colors.border, RoundedCornerShape(10.dp))
                .padding(6.dp)
        ) {
            content(onDismiss)
        }
    }
}
