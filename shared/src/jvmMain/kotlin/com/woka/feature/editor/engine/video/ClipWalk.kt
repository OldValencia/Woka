package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Stable
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.framesPerSecond
import com.woka.feature.editor.tools.trim.seekTargetOf
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.stepPicture
import com.woka.feature.editor.tools.trim.timelineOf
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.heldMs
import com.woka.feature.viewer.video.engine.playRanges
import com.woka.feature.viewer.video.engine.rangeIndex
import com.woka.feature.viewer.video.engine.seekInRanges
import com.woka.feature.viewer.video.engine.shownFrameMs
import kotlin.math.roundToLong

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
        // After a step, the picture it aimed at rather than the seek's target (ME-219).
        steppedPicture(sourceMs)?.let { return it.roundToLong() }
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

    /** The list the band last handed the player — what a key press steps through (ME-219). */
    var clips: List<TrimClip> = emptyList()

    /** The player's position a step left behind, and the timeline time of the picture it aimed at. */
    private var stepped: Pair<Long, Double>? = null

    /**
     * Moves the play head [direction] frames of the result — or seconds, [bySecond] — and
     * pauses first: a step is for looking at one picture, and a playing film moves on.
     *
     * **It counts from the picture on screen** (`FrameSteps`), and from the picture the last
     * step aimed at while the player still sits where that step left it: a seek inside the
     * source's last 200 ms pins the position to the end (`seek`), and a picture time kept
     * as a `Double` does not drift by a millisecond a step.
     */
    fun step(player: VideoPlayerState, direction: Int, bySecond: Boolean) {
        if (clips.isEmpty()) return
        val fps = player.frameRate()
        player.isPlaying = false
        val from = steppedPicture(player.currentPositionMs) ?: shownPicture(player)
        val frames = direction * if (bySecond) framesPerSecond(fps) else 1L
        val next = stepPicture(from, frames, clips.totalMs(), fps)
        seekTo(player, clips, seekTargetOf(next))
        stepped = player.currentPositionMs to next
    }

    private fun steppedPicture(sourceMs: Long): Double? = stepped?.takeIf { it.first == sourceMs }?.second

    /** The picture on screen on the timeline; a freeze's is its hold, which has no picture time. */
    private fun shownPicture(player: VideoPlayerState): Double {
        val at = index.coerceIn(0, clips.lastIndex)
        val shown = player.shownFrameMs
        if (clips[at].isFreeze || shown < 0L) return timelineMs(clips, player.currentPositionMs).toDouble()
        return clips.startOf(at) + (shown - clips[at].fromMs) / clips[at].speed.toDouble()
    }
}

/** The source's frame rate, or the render's own fallback when the container does not say. */
internal fun VideoPlayerState.frameRate(): Double =
    tracks.mediaTracks.video.firstOrNull { it.index == tracks.selectedVideoIndex }
        ?.frameRate?.takeIf { it.isFinite() && it > 1.0 } ?: FALLBACK_FPS

private const val FALLBACK_FPS = 30.0
