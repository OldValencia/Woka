package io.iris.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.theme.IrisTheme

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
    val topBarAccessories = LocalTopBarAccessories.current

    // Window drag, drag-to-restore and double-click maximize are handled by the
    // platform wrapper around the top bar (see desktop main.kt), not here.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 24.dp)
    ) {
        // Back Button on the left
        if (onNavigateBack != null) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                BackButton(onClick = topBarAccessories.backActionOverride ?: onNavigateBack)
            }
        }

        // Centered Content (Breadcrumbs or Accessory)
        Box(modifier = Modifier.align(Alignment.Center)) {
            if (topBarAccessories.centerAccessory != null) {
                topBarAccessories.centerAccessory?.invoke()
            } else if (currentPath != null) {
                SmartBreadcrumbs(
                    path = currentPath,
                    linkedFolders = linkedFolders,
                    onBreadcrumbClick = onBreadcrumbClick
                )
            }
        }

        // Win Controls & Right Accessory on the right
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            topBarAccessories.rightAccessory?.invoke()

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
            .pointerHoverIcon(PointerIcon.Hand).hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            "<",
            style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}


