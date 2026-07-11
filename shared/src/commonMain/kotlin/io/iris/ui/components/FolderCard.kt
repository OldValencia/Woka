package io.iris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import org.jetbrains.compose.resources.painterResource
import coil3.compose.AsyncImage
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import java.io.File

@Composable
fun FolderCard(
    path: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    showTitle: Boolean = true
) {
    val file = File(path)
    val folderName = file.name
    val isFolder = file.isDirectory

    // Find the first image in the folder to use as a cover, or use the file itself
    val coverImage = if (isFolder) {
        file.listFiles()?.firstOrNull { 
            it.isFile && (it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true)) 
        }
    } else {
        file
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(IrisTheme.colors.surface)
            .clickable(onClick = onClick)
    ) {
        if (coverImage != null) {
            AsyncImage(
                model = coverImage,
                contentDescription = "Cover for $folderName",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (showTitle || isFolder) {
                // Dark gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
            }
        } else {
            Image(
                painter = painterResource(IrisIcons.Static.Folder),
                contentDescription = "Empty Folder",
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                colorFilter = ColorFilter.tint(IrisTheme.colors.textSecondary)
            )
        }

        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                BasicText("✕", style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            }
        }

        if (showTitle || isFolder) {
            BasicText(
                text = folderName,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                style = TextStyle(
                    color = if (coverImage != null) Color.White else IrisTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
