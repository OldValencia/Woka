package io.iris.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import io.iris.ui.icons.IrisIcons
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.theme.IrisTheme
import java.io.File

import kotlin.math.log10
import kotlin.math.pow

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    val value = size / 1024.0.pow(digitGroups.toDouble())
    return "${(value * 10).toInt() / 10.0} ${units[digitGroups]}".replace(".0 ", " ")
}

@Composable
fun FolderCard(
    path: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    isSelected: Boolean = false,
    onContextAction: ((String) -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onModifierClick: ((Boolean, Boolean) -> Unit)? = null
) {
    val file = File(path)
    val name = file.name.takeIf { it.isNotEmpty() } ?: path
    val isDir = file.isDirectory
    
    val subtitleText = remember(path) {
        if (isDir) {
            "Folder"
        } else {
            val ext = file.extension.lowercase()
            val type = when (ext) {
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> "Image"
                "mp4", "mkv", "avi", "mov", "webm" -> "Video"
                else -> "File"
            }
            "$type • ${formatFileSize(file.length())}"
        }
    }
    
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val translateY by animateFloatAsState(targetValue = if (isHovered) -2f else 0f, animationSpec = tween(200))
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> IrisTheme.colors.accentFolders
            isHovered -> IrisTheme.colors.borderGlow
            else -> IrisTheme.colors.border
        }, 
        animationSpec = tween(200)
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(200)
    )
    val shadowElev = if (isHovered) 32.dp else 16.dp
    val titleColor by animateColorAsState(targetValue = if (isHovered || isSelected) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary, animationSpec = tween(200))

    var itemCount by remember(path) { mutableStateOf(0) }
    var previews by remember(path) { mutableStateOf<List<File>>(emptyList()) }
    
    LaunchedEffect(path) {
        if (isDir) {
            withContext(Dispatchers.IO) {
                val allFiles = file.listFiles() ?: emptyArray()
                
                itemCount = allFiles.count { f ->
                    val ext = f.extension.lowercase()
                    f.isDirectory || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "mp4", "mkv", "avi", "mov", "webm")
                }
                
                previews = allFiles.filter { f ->
                    f.isFile && f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "heic")
                }.take(3)
            }
        }
    }

    val dynamicBadgeText = if (isDir) {
        if (itemCount > 0) "$itemCount ITEMS" else "EMPTY"
    } else {
        file.extension.uppercase().takeIf { it.isNotEmpty() } ?: "FILE"
    }

    Column(
        modifier = modifier
            .graphicsLayer { translationY = translateY }
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .shadow(elevation = shadowElev, shape = RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                .background(IrisTheme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            // Background/Preview
            if (isDir) {
                Box(
                    modifier = Modifier.fillMaxSize().background(IrisTheme.colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (previews.isNotEmpty()) {
                        if (previews.size >= 3) {
                            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                coil3.compose.AsyncImage(
                                    model = previews[0],
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    coil3.compose.AsyncImage(
                                        model = previews[1],
                                        contentDescription = null,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentScale = ContentScale.Crop
                                    )
                                    coil3.compose.AsyncImage(
                                        model = previews[2],
                                        contentDescription = null,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        } else if (previews.size == 2) {
                            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                coil3.compose.AsyncImage(
                                    model = previews[0],
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentScale = ContentScale.Crop
                                )
                                coil3.compose.AsyncImage(
                                    model = previews[1],
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            coil3.compose.AsyncImage(
                                model = previews[0],
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(IrisIcons.Static.Folder),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            alpha = 0.5f,
                            colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.surfaceElevated)) {
                    coil3.compose.AsyncImage(
                        model = file,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(IrisTheme.colors.background.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
                    .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isDir) {
                        Image(
                            painter = painterResource(IrisIcons.Static.Folder),
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                        )
                    }
                    BasicText(
                        text = dynamicBadgeText,
                        style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        if (showTitle) {
            Column(modifier = Modifier.padding(horizontal = 2.dp)) {
                BasicText(
                    text = name,
                    style = TextStyle(color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = subtitleText,
                    style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 12.sp),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
