package com.woka.app.shell

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
import com.woka.core.ui.theme.Theme

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
        // Back Button on the left, and whatever the screen puts beside it
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onNavigateBack != null) {
                BackButton(onClick = topBarAccessories.backActionOverride ?: onNavigateBack)
            }

            // The path and a screen's leading accessory are one slot, not two: both
            // answer "what is this screen". Beside Back rather than centred in the
            // bar, because that is where Home's title starts too.
            //
            // Exclusive on purpose. `AnimatedContent` keeps the outgoing screen
            // composed for the length of the slide, so drawing both would put Home's
            // title beside the folder's path for 220 ms and then jump the path left
            // when Home was finally disposed. The path yields in turn to an overlay
            // that has taken the centre — the viewer and the editor speak for the
            // whole bar, and the editor's Revert wants this slot while they do.
            if (currentPath != null && topBarAccessories.centerAccessory == null) {
                SmartBreadcrumbs(
                    path = currentPath,
                    linkedFolders = linkedFolders,
                    onBreadcrumbClick = onBreadcrumbClick
                )
            } else {
                topBarAccessories.leadingAccessory?.invoke()
            }
        }

        // A Row rather than a Box since ME-173: the pair is centred, so an editor
        // control can sit beside the viewer's pill without being composed inside it.
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            topBarAccessories.centerAccessory?.invoke()
            topBarAccessories.centerTrailingAccessory?.invoke()
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
    val bg by animateColorAsState(if (isHovered) Theme.colors.surfaceHover else Color.Transparent, tween(150))

    Box(
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .pointerHoverIcon(PointerIcon.Hand).hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            "<",
            style = TextStyle(color = Theme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}


