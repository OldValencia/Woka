package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.clipOf
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.timelineOf
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.seek

/**
 * How close to a clip's end counts as reaching it — one frame at 50 fps.
 *
 * The presenter reports the position of the frame it just showed, so the last frame of a
 * clip arrives a frame *before* `toMs`; waiting for `toMs` itself would show one frame of
 * what was cut away at every join.
 */
private const val JOIN_TOLERANCE_MS = 20L

/**
 * Plays the clip list through the one player by seeking at every join (ME-210).
 *
 * **This is the stalling hand-off the author refused for the finished feature**
 * (`video-editor.md` §0): each join is a seek, so there is a short hold where the decoder
 * catches up. It is here because a cut the canvas does not honour is worse than a cut it
 * honours with a hitch — the editor would preview the very footage the user deleted. The
 * seamless hand-off is ME-232's pre-rolling decoder, and when it lands it replaces
 * [onPosition] and nothing else: the strip only ever asks for [timelineMs] and [seekTo].
 *
 * [index] is which clip is playing. The source position alone cannot say, because a
 * duplicated clip puts the same source range on the timeline twice.
 */
@Stable
internal class ClipWalk {

    var index by mutableIntStateOf(0)
        private set

    /**
     * How far into a freeze the play head is, and whether its hold is running (ME-217). A
     * freeze shows one frame with the player paused, so its time is the walk's own, advanced
     * by [tick] — the source position cannot move through it.
     */
    var heldMs by mutableLongStateOf(0L)
        private set
    var holding by mutableStateOf(false)
        private set
    private var frozen = false

    /** Whether the result is playing — the player, or a running hold. */
    fun isPlaying(player: VideoPlayerState): Boolean = player.isPlaying || holding

    /** Play/pause for the band's button and Space: a hold pauses itself, not the player. */
    fun toggle(player: VideoPlayerState) {
        if (frozen) holding = !holding else player.isPlaying = !player.isPlaying
    }

    /** Where the play head is on the timeline, derived from the player's source position. */
    fun timelineMs(clips: List<TrimClip>, sourceMs: Long): Long {
        val at = index.coerceIn(0, clips.lastIndex.coerceAtLeast(0))
        if (clips.getOrNull(at)?.isFreeze == true) return clips.startOf(at) + heldMs
        return clips.timelineOf(at, sourceMs)
    }

    /** Puts the play head at [timelineMs], which may be inside any clip. */
    fun seekTo(player: VideoPlayerState, clips: List<TrimClip>, timelineMs: Long) {
        val at = clips.atTimeline(timelineMs) ?: return
        val wasPlaying = isPlaying(player)
        enter(player, clips, at.index, wasPlaying)
        if (frozen) heldMs = (timelineMs - clips.startOf(at.index)).coerceAtLeast(0L)
        else player.seek(at.sourceMs)
    }

    /**
     * One step of the walk, called with every position the player reports and whenever
     * the list changes. Returns nothing: it acts on the player.
     *
     * - **A position outside the current clip** — a list edit took it away, or the file
     *   opened at 0 with the first clip starting later — moves to where it belongs: the
     *   clip that holds it, or the start of the nearest one.
     * - **The end of a clip** moves to the next one, or pauses at the end of the timeline,
     *   which is where the last frame stays on screen. A freeze ends in [tick] instead.
     */
    fun onPosition(player: VideoPlayerState, clips: List<TrimClip>, sourceMs: Long) {
        if (clips.isEmpty()) return
        // Re-read every time: an edit may have taken the freeze away from under the walk.
        frozen = clips.getOrNull(index)?.isFreeze == true
        if (frozen) return
        val current = clips.clipOf(sourceMs, index)
        if (current == null) {
            index = index.coerceIn(0, clips.lastIndex)
            player.seek(clips[index].fromMs)
            return
        }
        index = current
        val clip = clips[index]
        if (!player.isPlaying || sourceMs < clip.toMs - JOIN_TOLERANCE_MS) return
        if (index < clips.lastIndex) enter(player, clips, index + 1, playing = true) else player.isPlaying = false
    }

    /** [elapsedMs] more of a running hold; at its end, on to the next clip or the end. */
    fun tick(player: VideoPlayerState, clips: List<TrimClip>, elapsedMs: Long) {
        val clip = clips.getOrNull(index)?.takeIf { it.isFreeze } ?: return
        heldMs = (heldMs + elapsedMs).coerceAtMost(clip.holdMs)
        if (heldMs < clip.holdMs) return
        if (index < clips.lastIndex) enter(player, clips, index + 1, playing = true) else holding = false
    }

    /**
     * Play pressed with the play head on the timeline's last frame starts again from the
     * first clip — the viewer's own rule for a file that has reached its end.
     */
    fun onPlay(player: VideoPlayerState, clips: List<TrimClip>) {
        val atEnd = timelineMs(clips, player.currentPositionMs) >= clips.totalMs() - JOIN_TOLERANCE_MS * 2
        if (atEnd && clips.isNotEmpty()) seekTo(player, clips, 0L)
    }

    /** Clip [to] becomes current: a freeze parks the player on its frame and holds. */
    private fun enter(player: VideoPlayerState, clips: List<TrimClip>, to: Int, playing: Boolean) {
        index = to
        heldMs = 0L
        val clip = clips[to]
        frozen = clip.isFreeze
        holding = frozen && playing
        if (frozen) player.isPlaying = false
        player.seek(clip.fromMs)
        if (!frozen && playing) player.isPlaying = true
    }
}
