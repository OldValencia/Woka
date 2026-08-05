package com.woka.feature.viewer.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import com.woka.feature.viewer.shell.PopoverAnchor
import com.woka.feature.viewer.shell.ToolbarPopover
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * Standalone Settings ⚙ popover docked above its toolbar icon (§2.4). The scaffold
 * it used to carry — fade, alignment box, scrim card, hover reporting — is shared
 * with the Volume (§2.1) and Subtitle (§2.2) overlays as [ToolbarPopover].
 */
@Composable
fun PlayerSettingsOverlay(
    isVisible: Boolean,
    bottomPadding: Dp,
    alignment: Alignment = Alignment.BottomEnd,
    player: VideoPlayerState,
    onHover: (Boolean) -> Unit
) {
    ToolbarPopover(
        isVisible = isVisible,
        endPadding = PopoverAnchor.Settings,
        bottomPadding = bottomPadding,
        alignment = alignment,
        onHover = onHover
    ) {
        PlayerSettingsMenuList(player)
    }
}
