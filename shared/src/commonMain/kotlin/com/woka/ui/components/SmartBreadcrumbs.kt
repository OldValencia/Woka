package com.woka.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.ui.theme.Theme
import java.io.File

@Composable
fun SmartBreadcrumbs(path: String, linkedFolders: List<String>, onBreadcrumbClick: ((String) -> Unit)?) {
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
            .background(Theme.colors.surface, RoundedCornerShape(30.dp))
            .border(1.dp, Theme.colors.border, RoundedCornerShape(30.dp))
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        hierarchy.takeLast(3).forEachIndexed { index, f ->
            val name = f.name.takeIf { it.isNotEmpty() } ?: f.path
            val isLast = index == minOf(hierarchy.size, 3) - 1
            
            BasicText(
                text = name,
                style = TextStyle(
                    color = if (isLast) Theme.colors.textPrimary else Theme.colors.textSecondary,
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
                BasicText(text = " / ", style = TextStyle(color = Theme.colors.textTertiary.copy(alpha = 0.5f), fontSize = 15.sp))
            }
        }
    }
}
