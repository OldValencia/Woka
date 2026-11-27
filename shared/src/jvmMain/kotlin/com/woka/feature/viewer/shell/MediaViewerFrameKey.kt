package com.woka.feature.viewer.shell

import com.woka.core.ui.components.frames.step
import com.woka.core.ui.image.EditedClock
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.stepVideoFrame

/**
 * What `,` / `.` do in the viewer (RD-501, the prototype's `stepFrame`): an animation pauses
 * and steps, always; a film steps only while paused. Answers whether anything moved — a
 * playing film, a photograph and a burst do nothing (a burst has `PageUp`/`PageDown`).
 */
internal fun MediaViewerState.stepOneFrame(player: VideoPlayerState?, clock: EditedClock?, direction: Int): Boolean {
    frameTransport?.let { transport ->
        transport.step(direction)
        return true
    }
    if (player == null || player.isPlaying || player.durationMs <= 0L) return false
    stepVideoFrame(player, clock, direction)
    return true
}
