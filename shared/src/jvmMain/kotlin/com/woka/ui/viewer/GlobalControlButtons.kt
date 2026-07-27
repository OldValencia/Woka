package com.woka.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun ZoomOutButton(scale: Float, onScaleChange: (Float) -> Unit) {
    ViewerTooltip("Zoom Out") {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable {
                onScaleChange((scale - 0.2f).coerceAtLeast(0.1f))
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "-",
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.offset(y = (-1.5).dp)
            )
        }
    }
}

@Composable
fun ResetZoomButton(scale: Float, onScaleChange: (Float) -> Unit, onOffsetReset: () -> Unit) {
    ViewerTooltip("Reset Zoom") {
        Box(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable {
                onScaleChange(1f)
                onOffsetReset()
            }.padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${(scale * 100).toInt()}%",
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.offset(y = (-1).dp)
            )
        }
    }
}

@Composable
fun ZoomInButton(scale: Float, onScaleChange: (Float) -> Unit) {
    ViewerTooltip("Zoom In") {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable {
                onScaleChange((scale + 0.2f).coerceAtMost(10f))
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = TextStyle(
                    color = Theme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.offset(y = (-1.5).dp)
            )
        }
    }
}

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
fun CopyButton(copyTint: Color, onCopy: () -> Unit) {
    ViewerTooltip("Copy File") {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCopy() }
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Icons.Static.DocumentOnDocument),
                contentDescription = "Copy",
                tint = copyTint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ToggleCarouselButton(carouselMode: CarouselVisibility, onToggleCarousel: () -> Unit) {
    ViewerTooltip("Toggle Carousel") {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand)
                .clickable { onToggleCarousel() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    when (carouselMode) {
                        CarouselVisibility.HIDDEN -> Icons.Static.EyeSlash
                        else -> Icons.Static.Eye
                    }
                ),
                contentDescription = "Toggle Carousel",
                tint = when (carouselMode) {
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

@Composable
fun MagicWandButton() {
    ViewerTooltip("Magic Wand") {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Placeholder */ }
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Icons.Static.MagicWand),
                contentDescription = "Magic Wand",
                tint = Theme.colors.textPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
