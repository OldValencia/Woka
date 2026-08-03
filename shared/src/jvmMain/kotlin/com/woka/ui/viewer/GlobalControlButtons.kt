package com.woka.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.data.CarouselVisibility
import com.woka.ui.icons.Icons
import com.woka.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

@Composable
fun InfoButton(isInfoOpen: Boolean, onInfo: () -> Unit) {
    ViewerTooltip("Information") {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand)
                .clickable { onInfo() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Icons.Static.InfoCircle),
                contentDescription = "Information",
                tint = if (isInfoOpen) Theme.colors.accentFolders else Theme.colors.textPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ToggleCarouselButton(
    carouselMode: CarouselVisibility,
    onToggleCarousel: () -> Unit,
    isFullscreen: Boolean = false
) {
    ViewerTooltip(if (isFullscreen) "Авто скрытие в фуллскрин режиме" else "Toggle Carousel") {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    if (isFullscreen) Modifier
                    else Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onToggleCarousel() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    if (isFullscreen) Icons.Static.EyeSlash
                    else when (carouselMode) {
                        CarouselVisibility.HIDDEN -> Icons.Static.EyeSlash
                        else -> Icons.Static.Eye
                    }
                ),
                contentDescription = "Toggle Carousel",
                tint = if (isFullscreen) Theme.colors.textSecondary
                else when (carouselMode) {
                    CarouselVisibility.ALWAYS_ON -> Theme.colors.accentFolders
                    else -> Theme.colors.textPrimary
                },
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun EditButton(onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onEdit() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Icons.Static.Pencil),
            contentDescription = "Edit",
            tint = Theme.colors.textPrimary,
            modifier = Modifier.size(12.dp).padding(end = 4.dp)
        )
        Text(
            text = "Edit",
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.offset(y = (-1).dp)
        )
    }
}
