package io.iris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.unit.IntOffset
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
    showTitle: Boolean = true,
    isSelected: Boolean = false,
    onContextAction: ((action: String) -> Unit)? = null,
    onModifierClick: ((isShift: Boolean, isAlt: Boolean) -> Unit)? = null
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

    var contextMenuExpanded by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(IrisTheme.colors.surface)
            .then(if (isSelected || contextMenuExpanded) Modifier.border(4.dp, IrisTheme.colors.accent) else Modifier)
            .then(
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                if (onContextAction != null) {
                                    val position = event.changes.first().position
                                    contextMenuPosition = IntOffset(position.x.toInt(), position.y.toInt())
                                    contextMenuExpanded = true
                                    event.changes.forEach { it.consume() }
                                }
                            } else if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                val isShift = event.keyboardModifiers.isShiftPressed
                                val isAlt = event.keyboardModifiers.isAltPressed
                                if (isShift || isAlt) {
                                    onModifierClick?.invoke(isShift, isAlt)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
            )
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

        if (contextMenuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
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

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, IrisTheme.colors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(IrisTheme.colors.accent)
                )
            }
        }

        if (contextMenuExpanded && onContextAction != null) {
            Popup(
                offset = contextMenuPosition,
                onDismissRequest = { contextMenuExpanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .shadow(16.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(IrisTheme.colors.surface)
                        .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    ContextMenuItem("Rename", IrisTheme.colors.textPrimary) { contextMenuExpanded = false; onContextAction("Rename") }
                    ContextMenuItem("Copy", IrisTheme.colors.textPrimary) { contextMenuExpanded = false; onContextAction("Copy") }
                    ContextMenuItem("Duplicate", IrisTheme.colors.textPrimary) { contextMenuExpanded = false; onContextAction("Duplicate") }
                    ContextMenuItem("Move", IrisTheme.colors.textPrimary) { contextMenuExpanded = false; onContextAction("Move") }
                    ContextMenuItem("Delete", Color.Red) { contextMenuExpanded = false; onContextAction("Delete") }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        BasicText(text, style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}
