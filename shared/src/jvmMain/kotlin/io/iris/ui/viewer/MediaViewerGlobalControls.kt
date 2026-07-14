package io.iris.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import org.jetbrains.compose.resources.painterResource
import io.iris.data.CarouselVisibility

@Composable
fun MediaViewerGlobalControls(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetReset: () -> Unit,
    carouselMode: CarouselVisibility,
    onToggleCarousel: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(30.dp), spotColor = Color.Black.copy(alpha = 0.1f))
            .background(IrisTheme.colors.surface, RoundedCornerShape(30.dp))
            .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(30.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Zoom Out
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).clickable {
                onScaleChange((scale - 0.2f).coerceAtLeast(0.1f))
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "-",
                style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.offset(y = (-1.5).dp)
            )
        }

        // Reset Zoom
        Box(
            modifier = Modifier.clickable {
                onScaleChange(1f)
                onOffsetReset()
            }.padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${(scale * 100).toInt()}%",
                style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.offset(y = (-1).dp)
            )
        }

        // Zoom In
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).clickable {
                onScaleChange((scale + 0.2f).coerceAtMost(10f))
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.offset(y = (-1.5).dp)
            )
        }
        
        // Divider
        Box(modifier = Modifier.height(16.dp).width(1.dp).background(IrisTheme.colors.border))

        // Toggle Carousel Button
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).clickable { onToggleCarousel() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    when (carouselMode) {
                        CarouselVisibility.HIDDEN -> IrisIcons.Static.EyeSlash
                        else -> IrisIcons.Static.Eye
                    }
                ),
                contentDescription = "Toggle Carousel",
                tint = when (carouselMode) {
                    CarouselVisibility.ALWAYS_ON -> IrisTheme.colors.accentFolders
                    else -> IrisTheme.colors.textPrimary
                },
                modifier = Modifier.size(16.dp)
            )
        }
        
        // Divider
        Box(modifier = Modifier.height(16.dp).width(1.dp).background(IrisTheme.colors.border))

        // Edit Button
        Row(
            modifier = Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onEdit() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(IrisIcons.Static.Pencil),
                contentDescription = "Edit",
                tint = IrisTheme.colors.textPrimary,
                modifier = Modifier.size(12.dp).padding(end = 4.dp)
            )
            Text(
                text = "Edit",
                style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.offset(y = (-1).dp)
            )
        }
    }
}
