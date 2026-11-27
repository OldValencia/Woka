package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.frames.FrameTransport
import com.woka.core.ui.components.frames.seekTo
import com.woka.feature.viewer.shell.HoverTarget
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.feature.viewer.shell.PopoverHoverEffect
import com.woka.feature.viewer.shell.PopoverHoverState
import com.woka.feature.viewer.shell.ViewerTooltip
import com.woka.feature.viewer.video.FrameSpeedPopover
import com.woka.feature.viewer.video.FrameTransportBar
import com.woka.feature.viewer.video.roomTransport
import java.io.File

/** `.viewer-bottom` in the room: 32 from the sides, 20 from the foot. */
private val BAR_SIDE = 32.dp
private val BAR_BOTTOM = 20.dp

/** Above the bar: its foot, its 48 dp and a gap. */
private val SPEED_POPOVER_BOTTOM = 76.dp

/** `.gif-media .frame-column { max-height: calc(100% - 100px) }`: the strip stops above the bar. */
private val STRIP_BOTTOM = 100.dp

/**
 * A GIF in the editor (RD-501, `#editor/gif`): the frame strip top-left and the viewer's
 * transport under the picture, both in the room's colours. **No scope line** — the edit
 * stays whole (ME-138); a click on a frame or a step pauses there and changes nothing else.
 *
 * Both move the engine's own clock ([transport]), never the session's frame: the engine is
 * keyed on that, and a strip click must not reopen it.
 */
@Composable
internal fun BoxScope.EditorAnimationBand(transport: FrameTransport, file: File) {
    val hover = remember { PopoverHoverState() }
    PopoverHoverEffect(hover)
    val item = remember(file) { MediaItem.Image(file) }

    EditorFrameStrip(
        file = file,
        frameIndex = transport.frameIndex,
        onSelectFrame = transport::seekTo,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 12.dp, top = 16.dp, bottom = STRIP_BOTTOM)
    )
    FrameTransportBar(
        transport = transport,
        colors = roomTransport(),
        tooltip = { text, content -> ViewerTooltip(text, content = content) },
        loadPreview = { frame -> MediaThumbnails.loadPreview(item, frame) },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = BAR_SIDE, end = BAR_SIDE, bottom = BAR_BOTTOM),
        onSpeedHover = { hover.setHovered(HoverTarget.Speed, it) }
    )
    FrameSpeedPopover(
        isVisible = hover.visibleTarget == HoverTarget.Speed,
        transport = transport,
        bottomPadding = SPEED_POPOVER_BOTTOM,
        alignment = Alignment.BottomEnd,
        onHover = { hover.setHovered(HoverTarget.Speed, it) }
    )
}
