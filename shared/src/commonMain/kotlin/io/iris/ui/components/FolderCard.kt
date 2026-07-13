package io.iris.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.window.Popup

import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    val value = size / 1024.0.pow(digitGroups.toDouble())
    return "${(value * 10).toInt() / 10.0} ${units[digitGroups]}".replace(".0 ", " ")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FolderCard(
    path: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    isSelected: Boolean = false,
    isHighlighted: Boolean = false,
    contextMenuContent: (@Composable (dismissMenu: () -> Unit) -> Unit)? = null,
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
    
    var activeHighlight by remember(isHighlighted) { mutableStateOf(isHighlighted) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            activeHighlight = true
            kotlinx.coroutines.delay(2000.milliseconds)
            activeHighlight = false
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val translateY by animateFloatAsState(targetValue = if (isHovered || activeHighlight) -2f else 0f, animationSpec = tween(200))
    val borderColor by animateColorAsState(
        targetValue = when {
            activeHighlight -> IrisTheme.colors.accentFolders
            isSelected -> IrisTheme.colors.accentFolders
            isHovered -> IrisTheme.colors.borderGlow
            else -> IrisTheme.colors.border
        }, 
        animationSpec = tween(if (activeHighlight) 300 else 200)
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected || activeHighlight) 2.dp else 1.dp,
        animationSpec = tween(if (activeHighlight) 300 else 200)
    )
    val shadowElev = if (isHovered || activeHighlight || isSelected) 32.dp else 16.dp
    val glowColor by animateColorAsState(
        targetValue = if (isSelected) (if (isDir) IrisTheme.colors.accentFolders else IrisTheme.colors.accent) else Color.Black.copy(alpha = 0.35f),
        animationSpec = tween(300)
    )
    val titleColor by animateColorAsState(targetValue = if (isHovered || isSelected || activeHighlight) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary, animationSpec = tween(200))

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

    var altPressed by remember { mutableStateOf(false) }
    var shiftPressed by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier = modifier
            .graphicsLayer { translationY = translateY }
            .pointerHoverIcon(PointerIcon.Hand)
            .hoverable(interactionSource)
            .onPointerEvent(PointerEventType.Press) { event ->
                altPressed = event.keyboardModifiers.isAltPressed
                shiftPressed = event.keyboardModifiers.isShiftPressed
                if (event.button == PointerButton.Secondary && contextMenuContent != null) {
                    val change = event.changes.first()
                    if (!change.isConsumed) {
                        contextMenuOffset = IntOffset(change.position.x.toInt() + 2, change.position.y.toInt() + 2)
                        showContextMenu = true
                        change.consume()
                    }
                }
            }
            .clickable(interactionSource = interactionSource, indication = null) {
                if (onModifierClick != null && (altPressed || shiftPressed)) {
                    onModifierClick(altPressed, shiftPressed)
                } else {
                    onClick()
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .shadow(
                    elevation = shadowElev, 
                    shape = RoundedCornerShape(10.dp), 
                    spotColor = glowColor,
                    ambientColor = if (isSelected) glowColor.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.1f)
                )
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
                    
                    val ext = file.extension.lowercase()
                    if (ext in listOf("mp4", "mkv", "avi", "mov", "webm")) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(IrisIcons.Static.PlayFill),
                                contentDescription = "Video",
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
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
            if (onRemove != null && isHovered) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .width(38.dp)
                        .height(24.dp)
                        .background(IrisTheme.colors.background.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onRemove() }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(IrisIcons.Static.Close),
                        contentDescription = "Remove",
                        modifier = Modifier.size(12.dp),
                        colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
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

        if (showContextMenu && contextMenuContent != null) {
            Popup(
                alignment = Alignment.TopStart,
                offset = contextMenuOffset,
                onDismissRequest = { showContextMenu = false }
            ) {
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                        .background(IrisTheme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                        .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(10.dp))
                        .padding(6.dp)
                ) {
                    contextMenuContent { showContextMenu = false }
                }
            }
        }
    }
}
