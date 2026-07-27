package com.woka.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.ui.theme.Theme

data class FolderCardStyle(
    val translateY: Float,
    val borderColor: Color,
    val borderWidth: Dp,
    val shadowElev: Dp,
    val glowColor: Color,
    val titleColor: Color
)

@Composable
fun rememberFolderCardStyle(
    isHovered: Boolean,
    activeHighlight: Boolean,
    isSelected: Boolean,
    isDir: Boolean
): FolderCardStyle {
    val translateY by animateFloatAsState(targetValue = if (isHovered || activeHighlight) -2f else 0f, animationSpec = tween(200))
    val borderColor by animateColorAsState(
        targetValue = when {
            activeHighlight -> Theme.colors.accentFolders
            isSelected -> Theme.colors.accentFolders
            isHovered -> Theme.colors.borderGlow
            else -> Theme.colors.border
        },
        animationSpec = tween(if (activeHighlight) 300 else 200)
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected || activeHighlight) 2.dp else 1.dp,
        animationSpec = tween(if (activeHighlight) 300 else 200)
    )
    val shadowElev = if (isHovered || activeHighlight || isSelected) 32.dp else 16.dp
    val glowColor by animateColorAsState(
        targetValue = if (isSelected) (if (isDir) Theme.colors.accentFolders else Theme.colors.accent) else Color.Black.copy(alpha = 0.35f),
        animationSpec = tween(300)
    )
    val titleColor by animateColorAsState(targetValue = if (isHovered || isSelected || activeHighlight) Theme.colors.textPrimary else Theme.colors.textSecondary, animationSpec = tween(200))

    return FolderCardStyle(translateY, borderColor, borderWidth, shadowElev, glowColor, titleColor)
}
