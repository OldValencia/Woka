package com.woka.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.woka.core.ui.components.floatingPanel

@Composable
fun LibraryTopAccessory(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.floatingPanel(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = "$selectedCount selected",
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Box(modifier = Modifier.width(1.dp).height(20.dp).background(Theme.colors.border))

        AccessoryPill("Select All", enabled = true, onClick = onSelectAll, isSmall = true)
        AccessoryPill("Duplicate", enabled = selectedCount > 0, onClick = onDuplicate, isSmall = true)
        AccessoryPill("Copy", enabled = selectedCount > 0, onClick = onCopy, isSmall = true)
        AccessoryPill("Move", enabled = selectedCount > 0, onClick = onMove, isSmall = true)
        AccessoryPill("Delete", enabled = selectedCount > 0, onClick = onDelete, isDanger = true, isSmall = true)
    }
}

@Composable
fun AccessoryPill(
    text: String,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    isDanger: Boolean = false,
    isSmall: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val baseModifier = Modifier
        .clip(RoundedCornerShape(14.dp))
        .background(if (isActive) Theme.colors.surfacePressed else if (isHovered) Theme.colors.surfaceHover else Color.Transparent)
        .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)

    val finalModifier = if (isSmall) {
        baseModifier.padding(horizontal = 12.dp, vertical = 6.dp)
    } else {
        Modifier.height(40.dp).then(baseModifier).padding(horizontal = 12.dp)
    }

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center
    ) {
        val color =
            if (!enabled) Theme.colors.textSecondary else if (isDanger) Theme.colors.danger else if (isActive) Theme.colors.accentFolders else if (isHovered) Theme.colors.textPrimary else Theme.colors.textTertiary
        BasicText(text, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}
