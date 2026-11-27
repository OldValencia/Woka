package com.woka.feature.viewer.video

import com.woka.core.ui.image.EditedClock
import com.woka.feature.editor.tools.trim.seekTargetOf
import com.woka.feature.editor.tools.trim.stepPicture
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.frameRate
import com.woka.feature.viewer.video.engine.shownFrameMs

/**
 * `,` / `.` on a paused film in the viewer (RD-501): one picture back or on, on the timeline
 * the slider shows — the result's for a film `Done` cut ([clock]), the source's otherwise.
 *
 * The same arithmetic as the editor's step (`FrameSteps`, ME-219): counted from the
 * picture on screen, not from the play head, and the seek aims just before the wanted
 * picture, because the player shows the first one at or after its target.
 */
internal fun stepVideoFrame(player: VideoPlayerState, clock: EditedClock?, direction: Int) {
    val shown = player.shownFrameMs.takeIf { it >= 0L } ?: player.currentPositionMs
    val from = clock?.toTimeline(shown) ?: shown
    val total = clock?.totalMs ?: player.durationMs
    val next = stepPicture(from.toDouble(), direction.toLong(), total, player.frameRate())
    seekOnResult(player, clock, seekTargetOf(next))
}
