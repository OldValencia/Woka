package com.woka.feature.viewer.video.engine.decode

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import com.woka.feature.viewer.video.engine.setClipPace
import com.woka.feature.viewer.video.engine.showRange
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

/** The marker the decode loop sends past the last kept range (ME-232); -2 is end of file. */
internal const val END_OF_EDIT_TS = -3L

/** How often a freeze's hold advances and publishes `heldMs`. */
private const val HOLD_TICK_MS = 20L

/**
 * A decoded video frame tagged with the seek generation it was produced under. Moved out
 * of `VideoPlayerPresenter` when ME-232's join fields took that file past the size mandate.
 */
internal data class VideoFrame(
    val bitmap: ImageBitmap,
    val tsMs: Long,
    val generation: Int,
    val hdrInfo: HdrInfo? = null,
    // Last, with a default, so the end-of-stream marker in VideoPlayerSpeed.kt —
    // which carries a 1x1 placeholder that nothing retains — stays as it was.
    val id: Long = 0L,
    /** The kept range this frame opens, or -1 for a frame inside one (ME-232). */
    val joinIndex: Int = -1,
    /** For a freeze opened by a seek into its hold: how far in the hold already is. */
    val heldFromMs: Long = 0L
)

/** The picture stopped on the last kept range's last frame; Play starts the result over. */
internal fun VideoPlayerState.stopAtEditEnd() {
    isPlaying = false
    edit.joins.endShown = true
}

/** Whether the join into range [index] is a freeze (ME-217), whose frame the presenter holds. */
internal fun VideoPlayerState.isFreezeRange(index: Int): Boolean =
    edit.joins.ranges.getOrNull(index)?.isFreeze == true

/**
 * Holds a freeze's one frame on screen for what is left of its hold, counting only while
 * playing — so Pause during a hold pauses it, which the walks' frame-clock holds could not.
 * Ends early on a seek; the frames behind it are then stale and dropped.
 */
internal suspend fun VideoPlayerState.holdFreeze(index: Int, fromMs: Long, frameGeneration: Int) {
    val holdMs = edit.joins.ranges.getOrNull(index)?.holdMs ?: return
    var held = fromMs
    var last = System.nanoTime() / 1_000_000L
    while (held < holdMs && generation.seek == frameGeneration && currentCoroutineContext().isActive) {
        delay(HOLD_TICK_MS.milliseconds)
        val now = System.nanoTime() / 1_000_000L
        if (isPlaying && !isSeeking) held = (held + now - last).coerceAtMost(holdMs)
        last = now
        val shown = held
        Snapshot.withMutableSnapshot { edit.joins.heldMs = shown }
    }
    edit.joins.holdEnded = true
}

/**
 * The joined range's pace (ME-216), applied **before** the join frame is scheduled: a pace
 * change asks for a presenter rebase, and taken here it is consumed on this frame rather
 * than on the next one, which would then be shown a frame early.
 */
internal fun VideoPlayerState.paceJoin(index: Int) {
    val range = edit.joins.ranges.getOrNull(index) ?: return
    Snapshot.withMutableSnapshot { setClipPace(range.speed, edit.joins) }
}

/** A join on screen: which range, where the play head is, and the sound sent after it. */
internal fun VideoPlayerState.presentJoin(index: Int, heldFromMs: Long, frameTsMs: Long) {
    showRange(index, heldFromMs)
    currentPositionMs = frameTsMs
    edit.joins.requestAudioJoin(frameTsMs)
}
