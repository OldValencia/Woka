package com.woka.app.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.FolderInfoTooltip
import com.woka.core.ui.theme.Theme
import java.io.File

/**
 * Where you are, as plain text beside the Back button.
 *
 * It used to be a shadowed, bordered pill floating in the middle of the bar — a
 * container that looked like a control, did not behave like one, and drifted
 * sideways every time the path changed length. Without it the two screens have the
 * same anatomy: identity on the left, nothing on the right.
 *
 * Losing the container costs the affordance the container was providing, so each
 * segment answers hover with its colour instead. The last one is where you already
 * are, so it stays [Theme.colors.textPrimary] and gains nothing from being hovered.
 */
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        hierarchy.takeLast(3).forEachIndexed { index, f ->
            val isLast = index == minOf(hierarchy.size, 3) - 1
            Crumb(file = f, isLast = isLast, onClick = onBreadcrumbClick)

            if (!isLast) {
                BasicText(
                    text = "›",
                    style = TextStyle(color = Theme.colors.textTertiary.copy(alpha = 0.6f), fontSize = 15.sp),
                    modifier = Modifier.widthIn(min = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun Crumb(file: File, isLast: Boolean, onClick: ((String) -> Unit)?) {
    val name = file.name.takeIf { it.isNotEmpty() } ?: file.path
    val source = remember { MutableInteractionSource() }
    val isHovered by source.collectIsHoveredAsState()

    // Whether the 200.dp cap actually bit. Asked of the layout rather than guessed
    // from the character count: what fits is a question about the glyphs, and only
    // the one that measured them can answer it.
    var ellipsised by remember(file.absolutePath) { mutableStateOf(false) }

    val color by animateColorAsState(
        targetValue = when {
            isLast -> Theme.colors.textPrimary
            isHovered -> Theme.colors.textPrimary
            else -> Theme.colors.textSecondary
        },
        animationSpec = tween(150)
    )

    FolderInfoTooltip(file = file, overflowName = name.takeIf { ellipsised }) {
        BasicText(
            text = name,
            style = TextStyle(
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { ellipsised = it.hasVisualOverflow },
            modifier = Modifier
                .widthIn(max = 200.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = source, indication = null) {
                    onClick?.invoke(file.absolutePath)
                }
        )
    }
}
