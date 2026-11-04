package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.woka.core.ui.image.SoundMix
import com.woka.core.ui.image.soundGain
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.releaseClipGain
import com.woka.feature.viewer.video.engine.setClipGain

/**
 * The edit's sound while the editor plays (ME-240): the shared layer's mute, volume and fades
 * over the whole timeline, times the playing clip's own — so a fade is heard as it is made.
 * The shared layer is the document less the open clip's own work, under This Clip.
 */
@Composable
internal fun MixClipSound(
    player: VideoPlayerState,
    walk: ClipWalk,
    clips: List<TrimClip>,
    document: EditDocument,
    edits: EditorTimelineScope
) {
    val open = edits.openClip
    val shared = document.ops - open?.let { edits.ownOps(it).toSet() }.orEmpty()
    val whole = SoundTool.mixIn(shared)
    val own = clips.associate { it.key to SoundTool.mixIn(edits.ownOps(it.key)) }
    val owner = remember { Any() }
    // An edit with its sound as recorded asks the player for nothing at all; the gain one set
    // before (an undo took it away) is given back by the effect below leaving the composition.
    if (whole == null && own.values.all { it == null }) return
    val current by rememberUpdatedState(Triple(clips, whole, own))
    // On every position the player reports — not on every UI frame, which would keep the
    // window busy for as long as the film plays.
    LaunchedEffect(player, walk) {
        snapshotFlow { player.currentPositionMs to walk.index }
            .collect { player.setClipGain(current.gainAt(player, walk), owner) }
    }
    LaunchedEffect(whole, own) { player.setClipGain(current.gainAt(player, walk), owner) }
    DisposableEffect(player) { onDispose { player.releaseClipGain(owner) } }
}

private fun Triple<List<TrimClip>, SoundMix?, Map<Int, SoundMix?>>.gainAt(player: VideoPlayerState, walk: ClipWalk): Float {
    val (clips, whole, own) = this
    val clip = clips.getOrNull(walk.index) ?: return whole?.gainAt(player.currentPositionMs, player.durationMs) ?: 1f
    val at = walk.timelineMs(clips, player.currentPositionMs)
    val into = at - clips.startOf(walk.index)
    return soundGain(whole, own[clip.key], at, clips.totalMs(), into, clip.lengthMs)
}
