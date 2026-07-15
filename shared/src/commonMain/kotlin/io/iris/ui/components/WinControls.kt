package io.iris.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import io.iris.ui.theme.IrisTheme

@Composable
fun WinControls(onClose: () -> Unit, onMinimize: () -> Unit, onMaximize: () -> Unit, isMaximized: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WinBtn(onClick = onMinimize, glyph = { drawLine(it, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.2.dp.toPx()) })
        WinBtn(onClick = onMaximize, glyph = {
            if (isMaximized) {
                val i = size.width * 0.2f
                val s = Size(size.width - i, size.height - i)
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
fun WinBtn(onClick: () -> Unit, isClose: Boolean = false, glyph: DrawScope.(Color) -> Unit) {
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
