package com.woka.feature.viewer.video.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.woka.core.ui.image.SourceRange

/**
 * The kept ranges the player walks itself (ME-232): set by the editor's band or the viewer's
 * frame, followed by the decode loop, which hands off at each join on its own thread, and by
 * the presenter, which says which range is on screen.
 *
 * Before ME-232 the walk lived in Compose and noticed a join a frame after the presenter had
 * shown the last kept picture, while the decoder ran on into the cut; it then seeked. The
 * walks now only read [shownIndex] and [heldMs].
 */
internal class EditJoins {

    @Volatile
    var ranges: List<SourceRange> = emptyList()

    @Volatile
    var owner: Any? = null

    /** Bumped when [ranges] change, so the decode loop re-checks where it is. */
    @Volatile
    var listGen = 0

    /** The range the decoder is in — ahead of the screen by the frames queued for the presenter. */
    @Volatile
    var decoding = 0

    /** The range on screen, and how far into its hold a freeze is (ME-217). */
    var shownIndex by mutableIntStateOf(0)
    var heldMs by mutableLongStateOf(0L)

    /** The last range's end was sent to the presenter, and the presenter stopped on it. */
    @Volatile
    var endSent = false

    @Volatile
    var endShown = false

    /** The freeze being decoded has had its one picture sent down the channel. */
    @Volatile
    var freezeSent = false

    /** The presenter finished a hold, during which the decode loop stood on a full channel. */
    @Volatile
    var holdEnded = false

    /**
     * Bumped when the presenter shows a join: the picture moved to another range without a
     * seek, so the audio loop re-seeks to [audioJoinMs] without waiting on
     * `isVideoAtSeekTarget`, which no seek will ever satisfy.
     */
    @Volatile
    var audioJoin = 0
        private set

    @Volatile
    var audioJoinMs = 0L
        private set

    /** Target first, then the counter, as `PlaybackGeneration.requestSeek` does. */
    fun requestAudioJoin(toMs: Long) {
        audioJoinMs = toMs
        audioJoin++
    }

    val active: Boolean get() = ranges.isNotEmpty()
}

/** Which kept range is on screen; 0 for a film played whole. */
val VideoPlayerState.rangeIndex: Int get() = edit.joins.shownIndex

/** How far into a freeze's hold the result is (ME-217). */
val VideoPlayerState.heldMs: Long get() = edit.joins.heldMs

/**
 * Plays only [ranges], in their order, joining them without a seek where a lane is ready.
 * [owner] follows `setClipPace`'s rule — only the composition that set the list last can
 * take it back — for `setClipPace`'s reason: after `Done` two copies of the viewer's frame
 * overlap for a moment.
 */
fun VideoPlayerState.playRanges(ranges: List<SourceRange>, owner: Any) {
    val joins = edit.joins
    joins.owner = owner
    if (ranges == joins.ranges) return
    joins.ranges = ranges
    joins.listGen++
}

/** Back to the whole film — only if [owner] set the list last. */
fun VideoPlayerState.releaseRanges(owner: Any) {
    val joins = edit.joins
    if (joins.owner !== owner) return
    joins.owner = null
    joins.ranges = emptyList()
    joins.listGen++
    releaseClipPace(joins)
}

/**
 * Puts the play head in range [index]: [intoMs] is a source position in it, or for a freeze
 * how far into its hold.
 */
fun VideoPlayerState.seekInRanges(index: Int, intoMs: Long) {
    val joins = edit.joins
    val range = joins.ranges.getOrNull(index) ?: return
    joins.decoding = index
    joins.freezeSent = false
    joins.endSent = false
    joins.endShown = false
    showRange(index, if (range.isFreeze) intoMs.coerceIn(0L, range.holdMs) else 0L)
    seek(if (range.isFreeze) range.fromMs else intoMs.coerceIn(range.fromMs, range.toMs))
}

/**
 * Range [index] is the one on screen: its index, its hold, and its pace (ME-216). Written in
 * a mutable snapshot, because the presenter and the decode loop call it off the main thread
 * (`code-style/state.md`).
 */
internal fun VideoPlayerState.showRange(index: Int, heldMs: Long = 0L) {
    val joins = edit.joins
    val range = joins.ranges.getOrNull(index) ?: return
    Snapshot.withMutableSnapshot {
        joins.shownIndex = index
        joins.heldMs = heldMs
        setClipPace(range.speed, joins)
    }
}
