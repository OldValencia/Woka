package com.woka.feature.viewer.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.CarouselVisibility
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

@Composable
fun InfoButton(isInfoOpen: Boolean, onInfo: () -> Unit) {
    ViewerTooltip("Information") {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand)
                .clickable { onInfo() },
            contentAlignment = Alignment.Center
        ) {
            val tint = if (isInfoOpen) Theme.colors.accentFolders else Theme.colors.textPrimary
            Image(
                painter = painterResource(Icons.Static.InfoCircle),
                contentDescription = "Information",
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(tint)
            )
        }
    }
}

@Composable
fun ToggleCarouselButton(
    carouselMode: CarouselVisibility,
    onToggleCarousel: () -> Unit,
    isFullscreen: Boolean = false
) {
    ViewerTooltip(if (isFullscreen) "Dock auto-hides in fullscreen mode" else "Toggle Carousel") {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    if (isFullscreen) Modifier
                    else Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onToggleCarousel() }
                ),
            contentAlignment = Alignment.Center
        ) {
            val tint = if (isFullscreen) {
                Theme.colors.textSecondary
            } else when (carouselMode) {
                CarouselVisibility.ALWAYS_ON -> Theme.colors.accentFolders
                else -> Theme.colors.textPrimary
            }
            Image(
                painter = painterResource(
                    if (isFullscreen) Icons.Static.EyeSlash
                    else when (carouselMode) {
                        CarouselVisibility.HIDDEN -> Icons.Static.EyeSlash
                        else -> Icons.Static.Eye
                    }
                ),
                contentDescription = "Toggle Carousel",
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(tint)
            )
        }
    }
}

/**
 * Shrinks the fullscreen window into a PiP box, or fills the screen again.
 *
 * `photo_on_rectangle_angled` — a small frame over a large one — rather than
 * `Collapse`/`Expand`: the bottom control bar already uses those in this same
 * window, for leaving it entirely.
 */
@Composable
fun PipWindowButton(isPipWindow: Boolean, onToggle: () -> Unit) {
    val label = if (isPipWindow) "Fill the screen" else "Shrink to picture-in-picture"
    ViewerTooltip(label) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).pointerHoverIcon(PointerIcon.Hand)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            val tint = if (isPipWindow) Theme.colors.accentFolders else Theme.colors.textPrimary
            Image(
                painter = painterResource(Icons.Static.Navigation),
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(tint)
            )
        }
    }
}

/** Edit with the divider before it, so a pill without Edit ends flush on the carousel toggle. */
@Composable
internal fun EditGroup(onEdit: () -> Unit) {
    PillGap()
    PillDivider()
    PillGap()
    EditButton(onEdit)
}

@Composable
fun EditButton(onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onEdit() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(Icons.Static.Pencil),
            contentDescription = "Edit",
            modifier = Modifier.size(12.dp).padding(end = 4.dp),
            colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
        )
        BasicText(
            text = "Edit",
            style = TextStyle(
                color = Theme.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.offset(y = (-1).dp)
        )
    }
}
