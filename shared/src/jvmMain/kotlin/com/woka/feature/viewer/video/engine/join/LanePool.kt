package com.woka.feature.viewer.video.engine.join

import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.sync.landAt
import com.woka.feature.viewer.video.engine.sync.timeUs
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/**
 * Spare decoders beside the player's own, so four in all: 1.1–1.3 GB at four threads each,
 * and the slowest still decodes 4K at 41 fps with all four running (`DecoderPoolProbe`).
 */
internal const val MAX_LANES = 3

/**
 * How far ahead on the result a range must start to be given a lane — above the 2.2 s the
 * slowest measured pre-roll took (S02E01, ME-230), so a lane is parked before its join.
 */
internal const val LEAD_HORIZON_MS = 3_000L

/**
 * A lane left free this long is closed, giving its 270–330 MB back. Short on purpose — the
 * author's call, 2026-11-05: memory is the scarce thing, and reopening a lane costs a few
 * milliseconds of open plus a pre-roll it would have paid anyway.
 */
internal const val IDLE_CLOSE_MS = 3_000L

/**
 * The player's spare decoders (ME-232). **A free lane pre-rolls at once** rather than at a
 * computed lead: the lead is the keyframe distance, 0.3 s on one film and 2.2 s on another,
 * so any timer would be a guess per file. Lanes are opened only for ranges inside
 * [LEAD_HORIZON_MS], so an uncut film — one range — never holds more than the one decoder.
 *
 * Confined to the decode loop's thread.
 */
internal class LanePool(private val player: VideoPlayerState) {

    private val lanes = mutableListOf<DecoderLane>()

    /** Sends free lanes to the upcoming ranges nobody holds, and closes what is stale or idle. */
    suspend fun plan(ranges: List<SourceRange>, playing: Int, positionMs: Long) {
        lanes.filter { it.isStale }.forEach { closeLane(it) }
        val wanted = upcomingStarts(ranges, playing, positionMs)
        lanes.filter { lane -> !lane.isFree && wanted.none { lane.holds(it, ranges[it].fromMs) } }
            .forEach { it.letGo() }
        for (index in wanted) {
            if (lanes.any { it.holds(index, ranges[index].fromMs) }) continue
            val lane = lanes.firstOrNull { it.isFree } ?: newLane() ?: break
            lane.preRoll(index, ranges[index].fromMs)
        }
        val now = System.currentTimeMillis()
        lanes.filter { it.isFree && now - it.freeSinceMs > IDLE_CLOSE_MS }.forEach { closeLane(it) }
    }

    /** The lane rolling to or parked on range [index] starting at [startMs], if any. */
    fun laneFor(index: Int, startMs: Long): DecoderLane? = lanes.firstOrNull { it.holds(index, startMs) }

    suspend fun closeAll() {
        lanes.forEach { it.close() }
        lanes.clear()
    }

    private fun newLane(): DecoderLane? {
        if (lanes.size >= MAX_LANES) return null
        return DecoderLane(player).also { lanes += it }
    }

    private suspend fun closeLane(lane: DecoderLane) {
        lane.close()
        lanes.remove(lane)
    }
}

/** A lane's picture handed to the decode loop: the grabber to go on with, and where it stands. */
internal class Landed(val grabber: FFmpegFrameGrabber, val picture: Frame, val tsMs: Long)

/**
 * No lane holds the range: [grabber] seeks to it itself — the stall ME-232 exists to avoid,
 * kept as the fallback for a lane that could not open or a range nobody was sent to.
 */
internal fun landHere(player: VideoPlayerState, grabber: FFmpegFrameGrabber, range: SourceRange): Landed? {
    val picture = player.landAt(grabber, player.pictureFilter, range.fromMs) ?: return null
    return Landed(grabber, picture, player.pictureFilter.timeUs(grabber, picture) / 1000L)
}

/**
 * The ranges after [playing] that want a lane, nearest first: each one starting within
 * [LEAD_HORIZON_MS] of [positionMs] on the result, at most [MAX_LANES], skipping a range
 * that [continues] the one before it — the grabber flows straight into that one.
 */
internal fun upcomingStarts(ranges: List<SourceRange>, playing: Int, positionMs: Long): List<Int> {
    val current = ranges.getOrNull(playing) ?: return emptyList()
    var aheadMs = if (current.isFreeze) current.holdMs else ((current.toMs - positionMs).coerceAtLeast(0L) / current.speed).toLong()
    val wanted = mutableListOf<Int>()
    var index = playing + 1
    while (index <= ranges.lastIndex && aheadMs <= LEAD_HORIZON_MS && wanted.size < MAX_LANES) {
        if (!continues(ranges[index - 1], ranges[index])) wanted += index
        aheadMs += ranges[index].lengthMs
        index++
    }
    return wanted
}

/**
 * Whether [next] starts exactly where [previous] stops — a split clip given another speed —
 * so the join needs no seek at all. Never across a freeze: its grabber stands one frame past
 * the frame the next range would start on.
 */
internal fun continues(previous: SourceRange, next: SourceRange): Boolean =
    !previous.isFreeze && !next.isFreeze && next.fromMs == previous.toMs
