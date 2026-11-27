package com.woka.feature.viewer.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import com.woka.core.ui.components.frames.FrameTransport
import com.woka.feature.viewer.shell.PopoverAnchor
import com.woka.feature.viewer.shell.ToolbarPopover

/**
 * An animation's speed (RD-501): its own 0.25–5× track ([FrameSpeedSlider]) over the
 * animation's own value, docked above the bar's last slot. Only for looking: it opens at 1×
 * for every file and is never written anywhere (the author, 2026-11-27; keeping it is 1.1.0).
 */
@Composable
fun FrameSpeedPopover(
    isVisible: Boolean,
    transport: FrameTransport,
    bottomPadding: Dp,
    alignment: Alignment,
    onHover: (Boolean) -> Unit
) {
    ToolbarPopover(
        isVisible = isVisible,
        endPadding = PopoverAnchor.FrameSpeed,
        bottomPadding = bottomPadding,
        alignment = alignment,
        onHover = onHover
    ) {
        FrameSpeedSlider(speed = transport.speed, onSpeedChange = { transport.speed = it })
    }
}
