package com.woka.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource
import java.io.File
import com.woka.core.utils.generateVideoThumbnail
import coil3.compose.AsyncImage

@Composable
fun FolderCardPreview(
    isDir: Boolean,
    file: File,
    previews: List<File>,
    modifier: Modifier = Modifier
) {
    if (isDir) {
        Box(
            modifier = modifier.fillMaxSize().background(Theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (previews.isNotEmpty()) {
                if (previews.size >= 3) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        AsyncImage(
                            model = previews[0],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            AsyncImage(
                                model = previews[1],
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentScale = ContentScale.Crop
                            )
                            AsyncImage(
                                model = previews[2],
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                } else if (previews.size == 2) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        AsyncImage(
                            model = previews[0],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = previews[1],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    AsyncImage(
                        model = previews[0],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Image(
                    painter = painterResource(Icons.Static.Folder),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    alpha = 0.5f,
                    colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize().background(Theme.colors.surfaceElevated)) {
            val ext = file.extension.lowercase()
            val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "webm")
            
            if (isVideo) {
                var bitmap by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(file.absolutePath) {
                    bitmap = generateVideoThumbnail(file)
                }
                
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(Icons.Static.Video),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).align(Alignment.Center),
                        alpha = 0.5f,
                        colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
                    )
                }
            } else {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Icons.Static.PlayFill),
                        contentDescription = "Video",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
