package com.woka.feature.viewer.video.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.woka.core.ui.image.AppliedVideoEdit
import com.woka.core.ui.image.EditedClock
import com.woka.core.ui.image.SoundMix
import com.woka.core.ui.image.soundGain

/**
 * What `Done` did to a film's sound, heard in the viewer and PiP (ME-240): the whole film's
 * mute, volume and fades times the playing clip's own, set on the player at every position it
 * reports and given back when the edit goes. Nothing at all for a film whose sound is as
 * recorded.
 */
@Composable
fun PlayEditedSound(player: VideoPlayerState, edit: AppliedVideoEdit) {
    val whole = edit.wholeSound
    if (whole == null && edit.clock.ranges.none { it.sound != null }) return
    val owner = remember { Any() }
    // On every position the player reports, not on every UI frame.
    LaunchedEffect(player, edit) {
        snapshotFlow { player.currentPositionMs to edit.clock.index }
            .collect { player.setClipGain(gainNow(player, edit.clock, whole), owner) }
    }
    DisposableEffect(player, edit) { onDispose { player.releaseClipGain(owner) } }
}

private fun gainNow(player: VideoPlayerState, clock: EditedClock, whole: SoundMix?): Float {
    val ranges = clock.ranges
    if (ranges.isEmpty()) return whole?.gainAt(player.currentPositionMs, player.durationMs) ?: 1f
    val index = clock.index.coerceIn(0, ranges.lastIndex)
    val range = ranges[index]
    val at = clock.toTimeline(player.currentPositionMs)
    return soundGain(whole, range.sound, at, clock.totalMs, at - clock.startOf(index), range.lengthMs)
}
