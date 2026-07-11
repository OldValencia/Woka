package io.iris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
fun SelectionBottomMenu(
    onDeleteClick: (() -> Unit)? = null,
    onMoveClick: (() -> Unit)? = null,
    onCopyClick: (() -> Unit)? = null,
    onDuplicateClick: (() -> Unit)? = null,
    onUnlinkClick: (() -> Unit)? = null,
    onRenameClick: (() -> Unit)? = null,
    selectedCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IrisTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isEnabled = selectedCount > 0
        val isSingleSelection = selectedCount == 1
        
        if (onRenameClick != null) SelectionActionText("Rename", isSingleSelection, onRenameClick)
        if (onCopyClick != null) SelectionActionText("Copy", isEnabled, onCopyClick)
        if (onDuplicateClick != null) SelectionActionText("Duplicate", isEnabled, onDuplicateClick)
        if (onMoveClick != null) SelectionActionText("Move", isEnabled, onMoveClick)
        if (onUnlinkClick != null) SelectionActionText("Unlink", isEnabled, onUnlinkClick, isDestructive = true)
        if (onDeleteClick != null) SelectionActionText("Delete", isEnabled, onDeleteClick, isDestructive = true)
    }
}

@Composable
private fun SelectionActionText(text: String, enabled: Boolean, onClick: () -> Unit, isDestructive: Boolean = false) {
    val color = if (!enabled) IrisTheme.colors.textSecondary.copy(alpha = 0.5f)
                else if (isDestructive) Color.Red
                else IrisTheme.colors.accent
                
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium))
    }
}
