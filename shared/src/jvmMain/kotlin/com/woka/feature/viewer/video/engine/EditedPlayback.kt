package com.woka.feature.viewer.video.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.woka.core.ui.image.EditedClock

/**
 * Plays only what `Done` kept (ME-284): the viewer and PiP hand the film's kept ranges to
 * the player, which joins them in their order without a seek where a lane is ready, holds
 * a freeze and stops at the end of the last one (ME-232).
 *
 * **The editor's band does the same** with its clip list, and the two being one call is
 * what made ME-232 one change rather than two walks rewritten in parallel.
 *
 * The range on screen is mirrored into [EditedClock.index] and its hold into
 * [EditedClock.heldMs], so the slider places the play head on the result by the player's
 * own answer. Nothing for an empty list: an edit that cuts nothing plays the whole file.
 */
@Composable
fun PlayEditedRanges(player: VideoPlayerState, clock: EditedClock) {
    val ranges = clock.ranges
    if (ranges.isEmpty()) return
    // Owned by this composition: a copy of the frame leaving after `Done` must not take
    // back the list the copy replacing it has just set (`releaseRanges`).
    val owner = remember { Any() }
    LaunchedEffect(player, ranges) { player.playRanges(ranges, owner) }
    DisposableEffect(player) { onDispose { player.releaseRanges(owner) } }
    LaunchedEffect(player, clock) {
        snapshotFlow { player.rangeIndex to player.heldMs }.collect { (index, held) ->
            clock.index = index.coerceIn(0, ranges.lastIndex)
            clock.heldMs = held
        }
    }
}
