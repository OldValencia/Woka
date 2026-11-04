package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Stable
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.timelineOf
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.heldMs
import com.woka.feature.viewer.video.engine.playRanges
import com.woka.feature.viewer.video.engine.rangeIndex
import com.woka.feature.viewer.video.engine.seekInRanges

/**
 * Where in the clip list the play head is, and how the strip moves it (ME-210).
 *
 * **The player walks the list itself since ME-232**: the band hands it the clips as kept
 * ranges, and the decode loop joins them on its own thread — from a decoder that has
 * already paid the next clip's seek — holds a freeze, and stops at the end. This only reads
 * where it is and turns the strip's timeline positions into the player's.
 *
 * [index] is which clip is on screen. The source position alone cannot say, because a
 * duplicated clip puts the same source range on the timeline twice.
 */
@Stable
internal class ClipWalk(private val player: () -> VideoPlayerState?) {

    val index: Int get() = player()?.rangeIndex ?: 0

    /** Whether the result is playing — a freeze's hold included, which plays with the player. */
    fun isPlaying(player: VideoPlayerState): Boolean = player.isPlaying

    /** Play/pause for the band's button and Space; a hold pauses with the player. */
    fun toggle(player: VideoPlayerState) {
        player.isPlaying = !player.isPlaying
    }

    /** Where the play head is on the timeline, derived from the player's source position. */
    fun timelineMs(clips: List<TrimClip>, sourceMs: Long): Long {
        val at = index.coerceIn(0, clips.lastIndex.coerceAtLeast(0))
        if (clips.getOrNull(at)?.isFreeze == true) return clips.startOf(at) + (player()?.heldMs ?: 0L)
        return clips.timelineOf(at, sourceMs)
    }

    /**
     * Puts the play head at [timelineMs], which may be inside any clip. The list goes to the
     * player first: a strip gesture seeks in the list it has just made, before the band's
     * effect hands that list over.
     */
    fun seekTo(player: VideoPlayerState, clips: List<TrimClip>, timelineMs: Long) {
        val at = clips.atTimeline(timelineMs) ?: return
        player.playRanges(clips.map { it.toSourceRange() }, this)
        val into = if (clips[at.index].isFreeze) timelineMs - clips.startOf(at.index) else at.sourceMs
        player.seekInRanges(at.index, into)
    }
}
