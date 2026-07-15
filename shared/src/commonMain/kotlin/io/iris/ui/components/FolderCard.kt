package io.iris.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import io.iris.ui.theme.IrisTheme
import java.io.File
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.PointerButton



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
    
    val subtitleText = remember(path) { getFolderCardSubtitle(isDir, file) }
    
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
    
    val style = rememberFolderCardStyle(
        isHovered = isHovered,
        activeHighlight = activeHighlight,
        isSelected = isSelected,
        isDir = isDir
    )

    var itemCount by remember(path) { mutableStateOf(0) }
    var previews by remember(path) { mutableStateOf<List<File>>(emptyList()) }
    
    LaunchedEffect(path) {
        if (isDir) {
            val data = fetchFolderStateData(file)
            itemCount = data.itemCount
            previews = data.previews
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
            .graphicsLayer { translationY = style.translateY }
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
                    elevation = style.shadowElev, 
                    shape = RoundedCornerShape(10.dp), 
                    spotColor = style.glowColor,
                    ambientColor = if (isSelected) style.glowColor.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.1f)
                )
                .background(IrisTheme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                .border(style.borderWidth, style.borderColor, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
        ) {
            // Background/Preview
            FolderCardPreview(
                isDir = isDir,
                file = file,
                previews = previews,
                modifier = Modifier.fillMaxSize()
            )

            // Badge
            FolderCardBadge(
                isDir = isDir,
                dynamicBadgeText = dynamicBadgeText,
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            if (onRemove != null && isHovered) {
                FolderCardRemoveButton(
                    onRemove = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }

        if (showTitle) {
            FolderCardTitle(
                name = name,
                subtitleText = subtitleText,
                titleColor = style.titleColor
            )
        }
        }

        if (showContextMenu && contextMenuContent != null) {
            FolderCardContextMenu(
                offset = contextMenuOffset,
                onDismiss = { showContextMenu = false },
                content = contextMenuContent
            )
        }
    }
}
