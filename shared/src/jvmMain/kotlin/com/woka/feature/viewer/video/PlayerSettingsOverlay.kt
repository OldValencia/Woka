package com.woka.feature.viewer.video

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * Standalone Settings ⚙ popover docked above its toolbar icon (§2.4), matching
 * the architecture of the Volume (§2.1) and Subtitle (§2.2) overlays. Fixed width
 * so the content can grow without the cursor ever dropping out of the card.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerSettingsOverlay(
    isVisible: Boolean,
    bottomPadding: Dp,
    alignment: Alignment = Alignment.BottomEnd,
    videoState: VideoPlayerState?,
    onHover: (Boolean) -> Unit
) {
    if (videoState == null) return
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
                    // Sits one icon slot right of the Volume popover (end = 96.dp),
                    // matching the [Volume] [Settings] [Fullscreen] toolbar order.
                    .padding(end = 60.dp, bottom = bottomPadding)
                    // 210.dp matches the subtitle popover: the sync steppers need
                    // more room than a bare track list did.
                    .width(210.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp)
                    .onPointerEvent(PointerEventType.Enter) { onHover(true) }
                    .onPointerEvent(PointerEventType.Exit) { onHover(false) }
            ) {
                PlayerSettingsMenuList(videoState)
            }
        }
    }
}
