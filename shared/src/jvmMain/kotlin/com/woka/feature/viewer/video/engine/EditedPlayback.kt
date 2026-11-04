package com.woka.feature.viewer.video.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
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
    // A range's own pace (ME-216), multiplied into the user's speed by the player; back to
    // 1 when the edit goes — a Revert, another file.
    // Owned by this composition: a copy of the frame leaving after `Done` must not reset the
    // pace the copy replacing it has just set (`releaseClipPace`).
    val pace = ranges.getOrNull(clock.index)?.speed ?: 1f
    val owner = remember { Any() }
    LaunchedEffect(player, pace) { player.setClipPace(pace, owner) }
    DisposableEffect(player) { onDispose { player.releaseClipPace(owner) } }
    LaunchedEffect(player, clock) {
        // Where the walk itself stopped at the end: play pressed *there* starts again from
        // the first range — the viewer's own rule for a film that has ended — while a seek
        // away from it first forgets it.
        var endedAt: Long? = null
        snapshotFlow { player.currentPositionMs to player.isPlaying }.collect { (at, playing) ->
            if (player.durationMs <= 0L) return@collect
            // On a freeze the player stands on one frame; Play there runs the hold (ME-217).
            if (ranges.getOrNull(clock.index)?.isFreeze == true) {
                if (playing) clock.holdOn(player)
                return@collect
            }
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
                holding < 0 && next >= 0 -> clock.enter(player, next, playing)
                holding < 0 -> if (playing) {
                    player.isPlaying = false
                    endedAt = at
                }
                at !in ranges[clock.index].fromMs until ranges[clock.index].toMs -> clock.index = holding
                playing && at >= ranges[clock.index].toMs - JOIN_TOLERANCE_MS -> {
                    if (clock.index < ranges.lastIndex) {
                        clock.enter(player, clock.index + 1, playing = true)
                    } else {
                        player.isPlaying = false
                        endedAt = at
                    }
                }
            }
        }
    }
    // A freeze's hold runs on the frame clock, then the walk moves on (ME-217).
    LaunchedEffect(player, clock, clock.holding) {
        if (!clock.holding) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (clock.holding) {
            val now = withFrameMillis { it }
            val range = ranges.getOrNull(clock.index)?.takeIf { it.isFreeze } ?: break
            clock.heldMs = (clock.heldMs + now - last).coerceAtMost(range.holdMs)
            last = now
            if (clock.heldMs < range.holdMs) continue
            if (clock.index < ranges.lastIndex) clock.enter(player, clock.index + 1, playing = true) else clock.holding = false
        }
    }
}

/** Range [to] becomes current: a freeze parks the player on its frame, and holds if playing. */
private fun EditedClock.enter(player: VideoPlayerState, to: Int, playing: Boolean) {
    index = to
    heldMs = 0L
    val range = ranges[to]
    holding = range.isFreeze && playing
    if (range.isFreeze) player.isPlaying = false
    player.seek(range.fromMs)
    if (!range.isFreeze && playing) player.isPlaying = true
}

/**
 * Play pressed on a freeze: the player stays on its frame and the hold runs instead — or,
 * on a last freeze already held out, the film starts again, the viewer's rule at an end.
 */
private fun EditedClock.holdOn(player: VideoPlayerState) {
    val range = ranges[index]
    if (index == ranges.lastIndex && heldMs >= range.holdMs) return enter(player, 0, playing = true)
    player.isPlaying = false
    holding = true
}
