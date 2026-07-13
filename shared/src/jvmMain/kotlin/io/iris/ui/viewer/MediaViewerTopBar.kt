package io.iris.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.iris.ui.icons.IrisIcons
import org.jetbrains.compose.resources.painterResource

@Composable
fun MediaViewerTopBar(
    animationProgress: Float,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetReset: () -> Unit,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .offset(y = (-100).dp * animationProgress)
            .alpha(1f - animationProgress)
            .fillMaxWidth()
            .padding(16.dp)
            .height(48.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(IrisIcons.Static.Close),
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
        }

        // Zoom Controller
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable {
                    onScaleChange((scale - 0.2f).coerceAtLeast(0.5f))
                },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "-",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier.padding(horizontal = 8.dp).clickable {
                    onScaleChange(1f)
                    onOffsetReset()
                },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${(scale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable {
                    onScaleChange((scale + 0.2f).coerceAtMost(5f))
                },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .clickable { onEdit() }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(IrisIcons.Static.Pencil),
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp).padding(end = 6.dp)
            )
            Text(
                "Edit",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
