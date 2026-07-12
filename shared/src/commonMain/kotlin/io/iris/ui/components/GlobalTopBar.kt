package io.iris.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.theme.IrisTheme
import java.io.File

@Composable
fun GlobalTopBar(
    currentPath: String?,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onBreadcrumbClick: ((String) -> Unit)? = null,
    linkedFolders: List<String> = emptyList(),
    isMaximized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp)
    ) {
        // Back Button on the left
        if (onNavigateBack != null) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                BackButton(onClick = onNavigateBack)
            }
        }

        // Centered Breadcrumbs
        if (currentPath != null) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                SmartBreadcrumbs(
                    path = currentPath,
                    linkedFolders = linkedFolders,
                    onBreadcrumbClick = onBreadcrumbClick
                )
            }
        }

        // Win Controls on the right
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            WinControls(
                onClose = onClose,
                onMinimize = onMinimize,
                onMaximize = onMaximize,
                isMaximized = isMaximized
            )
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val isHovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (isHovered) IrisTheme.colors.surfaceHover else Color.Transparent, tween(150))
    
    Box(
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .pointerHoverIcon(PointerIcon.Hand).hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText("<", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SmartBreadcrumbs(path: String, linkedFolders: List<String>, onBreadcrumbClick: ((String) -> Unit)?) {
    val file = File(path)
    val matchedRoot = linkedFolders.firstOrNull { path.startsWith(it, ignoreCase = true) }

    var current: File? = file
    val hierarchy = mutableListOf<File>()
    while (current != null) {
        hierarchy.add(0, current)
        if (matchedRoot != null && current.absolutePath.equals(matchedRoot, ignoreCase = true)) {
            break
        }
        current = current.parentFile
    }

    Row(
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(30.dp), spotColor = Color.Black.copy(alpha = 0.1f))
            .background(IrisTheme.colors.surface, RoundedCornerShape(30.dp))
            .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(30.dp))
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        hierarchy.takeLast(3).forEachIndexed { index, f ->
            val name = f.name.takeIf { it.isNotEmpty() } ?: f.path
            val isLast = index == minOf(hierarchy.size, 3) - 1
            
            BasicText(
                text = name,
                style = TextStyle(
                    color = if (isLast) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { onBreadcrumbClick?.invoke(f.absolutePath) }
            )
            
            if (!isLast) {
                BasicText(text = " / ", style = TextStyle(color = IrisTheme.colors.textTertiary.copy(alpha = 0.5f), fontSize = 15.sp))
            }
        }
    }
}

@Composable
private fun WinControls(onClose: () -> Unit, onMinimize: () -> Unit, onMaximize: () -> Unit, isMaximized: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WinBtn(onClick = onMinimize, glyph = { drawLine(it, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.2.dp.toPx()) })
        WinBtn(onClick = onMaximize, glyph = {
            if (isMaximized) {
                val i = size.width * 0.2f
                val s = androidx.compose.ui.geometry.Size(size.width - i, size.height - i)
                drawRoundRect(it, topLeft = Offset(i, 0f), size = s, style = Stroke(1.2.dp.toPx()))
                drawRoundRect(it, topLeft = Offset(0f, i), size = s, style = Stroke(1.2.dp.toPx()))
            } else {
                drawRoundRect(it, size = size, style = Stroke(1.2.dp.toPx()))
            }
        })
        WinBtn(onClick = onClose, isClose = true, glyph = {
            drawLine(it, Offset(0f, 0f), Offset(size.width, size.height), 1.2.dp.toPx())
            drawLine(it, Offset(size.width, 0f), Offset(0f, size.height), 1.2.dp.toPx())
        })
    }
}

@Composable
private fun WinBtn(onClick: () -> Unit, isClose: Boolean = false, glyph: DrawScope.(Color) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val isHovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (isHovered && isClose) Color(0xFFE81123) else if (isHovered) IrisTheme.colors.surfaceHover else Color.Transparent, tween(150))
    val fg by animateColorAsState(if (isHovered && isClose) Color.White else if (isHovered) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary, tween(150))

    Box(
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .pointerHoverIcon(PointerIcon.Hand).hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Canvas(Modifier.size(11.dp)) { glyph(fg) } }
}
