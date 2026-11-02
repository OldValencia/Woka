package com.woka.feature.viewer.video.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.woka.core.ui.image.EditedClock
import kotlin.math.abs

/** One frame at 50 fps — `ClipWalk`'s tolerance, for `ClipWalk`'s reason. */
private const val JOIN_TOLERANCE_MS = 20L

/**
 * Plays only what `Done` kept (ME-284): the viewer and PiP walk the film's kept ranges in
 * their order, seeking at every join and stopping at the end of the last one.
 *
 * **The editor's `ClipWalk` in the viewer's vocabulary**, not a second design: the same
 * three rules — a position outside every range moves to where it belongs, the end of a
 * range seeks to the next, the end of the last pauses — over `SourceRange`, because the
 * viewer may not name the editor's `TrimClip`. When ME-232's seamless hand-off lands it
 * replaces both, and the two being identical is what makes that one change.
 *
 * The range it is in is [EditedClock.index], so the slider places the play head on the
 * result by the same answer the walk acts on. Nothing for an empty list: an edit that cuts nothing plays the whole file as before.
 * The slider still speaks source time; showing the edited length is ME-285.
 */
@Composable
fun PlayEditedRanges(player: VideoPlayerState, clock: EditedClock) {
    val ranges = clock.ranges
    if (ranges.isEmpty()) return
    LaunchedEffect(player, clock) {
        // Where the walk itself stopped at the end: play pressed *there* starts again from
        // the first range — the viewer's own rule for a film that has ended — while a seek
        // away from it first forgets it.
        var endedAt: Long? = null
        snapshotFlow { player.currentPositionMs to player.isPlaying }.collect { (at, playing) ->
            if (player.durationMs <= 0L) return@collect
            val stoppedAt = endedAt
            if (stoppedAt != null && abs(at - stoppedAt) > JOIN_TOLERANCE_MS * 5) endedAt = null
            if (playing && endedAt != null) {
                endedAt = null
                clock.index = 0
                player.seek(ranges.first().fromMs)
                return@collect
            }
            val holding = ranges.indexOfFirst { at in it.fromMs until it.toMs }
            val next = ranges.indexOfFirst { it.fromMs > at }
            when {
                // Moved here by a seek, the slider, or the file opening at 0.
                holding < 0 && next >= 0 -> {
                    clock.index = next
                    player.seek(ranges[next].fromMs)
                }
                holding < 0 -> if (playing) {
                    player.isPlaying = false
                    endedAt = at
                }
                at !in ranges[clock.index].fromMs until ranges[clock.index].toMs -> clock.index = holding
                playing && at >= ranges[clock.index].toMs - JOIN_TOLERANCE_MS -> {
                    if (clock.index < ranges.lastIndex) {
                        clock.index += 1
                        player.seek(ranges[clock.index].fromMs)
                    } else {
                        player.isPlaying = false
                        endedAt = at
                    }
                }
            }
        }
    }
}
