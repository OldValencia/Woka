package com.woka.feature.viewer.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/**
 * The key a file change takes, for the two buttons' tooltips (2026-11-05): `Alt` while
 * something plays, because there the bare arrows seek (`handleArrowKey`).
 */
internal fun fileKey(arrow: String, playing: Boolean): String = if (playing) "Alt+$arrow" else arrow

@Composable
fun MediaViewerPrevButton(
    animationProgress: Float,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier,
    /** A film or a track is open, so the key is `Alt+←`. */
    playing: Boolean = false
) {
    ViewerTooltip("Previous file   ·   ${fileKey("←", playing)}", modifier) {
        Box(
            modifier = Modifier
                .offset(x = (-100).dp * animationProgress)
                .alpha(1f - animationProgress)
                .padding(start = 16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { onPrev() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Icons.Static.ChevronRight),
                contentDescription = "Previous",
                modifier = Modifier.size(20.dp).rotate(180f),
                colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
            )
        }
    }
}

@Composable
fun MediaViewerNextButton(
    animationProgress: Float,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    /** A film or a track is open, so the key is `Alt+→`. */
    playing: Boolean = false
) {
    ViewerTooltip("Next file   ·   ${fileKey("→", playing)}", modifier) {
        Box(
            modifier = Modifier
                .offset(x = 100.dp * animationProgress)
                .alpha(1f - animationProgress)
                .padding(end = 16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Icons.Static.ChevronRight),
                contentDescription = "Next",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(Theme.colors.textPrimary)
            )
        }
    }
}
