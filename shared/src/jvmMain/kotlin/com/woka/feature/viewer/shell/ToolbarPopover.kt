package com.woka.feature.viewer.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * End offsets of the toolbar popovers, matching the icon order
 * `Subtitles Volume Settings Fullscreen` in `MediaViewerVideoControls`.
 * They were three magic numbers in three files; a change to the icon order has to
 * be reflected here and nowhere else.
 */
object PopoverAnchor {
    val Subtitles: Dp = 136.dp
    val Volume: Dp = 96.dp
    val Settings: Dp = 60.dp
}

/**
 * A popover docked above its toolbar icon. The volume, subtitle and settings
 * overlays were byte-identical scaffolds around their own content — same fade,
 * same alignment box, same scrim card, same hover reporting.
 *
 * Domain conditions ("no video", "no subtitles at all") stay in the callers: this
 * knows only how a popover looks, not when one is meaningful.
 *
 * [width] is null for the volume popover, which sizes to its content because the
 * optional audio-track list docks onto its side.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ToolbarPopover(
    isVisible: Boolean,
    endPadding: Dp,
    bottomPadding: Dp,
    alignment: Alignment,
    onHover: (Boolean) -> Unit,
    width: Dp? = Theme.viewer.popoverWidth,
    content: @Composable () -> Unit
) {
    // Fade only — a vertical slide made the popover briefly overlap the toolbar
    // icons during the transition, which re-fired Enter/Exit and flickered it.
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = alignment) {
            Box(
                modifier = Modifier
                    .padding(end = endPadding, bottom = bottomPadding)
                    .then(if (width != null) Modifier.width(width) else Modifier)
                    .background(
                        Theme.viewer.popoverScrim,
                        RoundedCornerShape(Theme.viewer.cornerRadius)
                    )
                    .padding(Theme.viewer.popoverPadding)
                    .onPointerEvent(PointerEventType.Enter) { onHover(true) }
                    .onPointerEvent(PointerEventType.Exit) { onHover(false) }
            ) {
                content()
            }
        }
    }
}
