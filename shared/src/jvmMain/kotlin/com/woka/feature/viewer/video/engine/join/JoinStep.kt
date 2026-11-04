package com.woka.feature.viewer.video.engine.join

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.decode.END_OF_EDIT_TS
import com.woka.feature.viewer.video.engine.decode.FramePacer
import com.woka.feature.viewer.video.engine.decode.VideoFrame
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/**
 * The decode loop's half of playing kept ranges (ME-232). The loop asks it three things:
 * whether to wait ([idle]), whether a freeze or a join replaces the next grab
 * ([beforeGrab], [pastEnd]), and whether a decoded picture is past its range ([step]).
 * Each answer that moves to another range hands back the grabber to go on with — the same
 * one, or a lane's, traded at the join exactly as a track reopen swaps grabbers.
 *
 * With no ranges set every call is a no-op, and the film plays as it did before ME-232.
 */
internal class JoinStep(
    private val player: VideoPlayerState,
    private val channel: Channel<VideoFrame>,
    private val pacer: FramePacer
) {
    private val joins = player.edit.joins
    private var pool: LanePool? = null
    private var listGen = -1

    /** Where the decoder is in the source — ahead of the screen, and what the lanes plan from. */
    private var decodedMs = 0L

    val active: Boolean get() = joins.active

    /**
     * Follows an edit of the list, plans the lanes, and holds the loop still while the last
     * range's end is on its way to the screen. Play pressed on that end starts the result
     * over. True when the loop should wait rather than decode.
     */
    suspend fun idle(): Boolean {
        if (listGen != joins.listGen) {
            listGen = joins.listGen
            if (joins.active) relocate(player, player.currentPositionMs) else close()
        }
        if (!joins.active) return false
        // The loop sat on a full channel through a freeze's hold; its pacing anchor is that old.
        if (joins.holdEnded) {
            joins.holdEnded = false
            pacer.rebase()
        }
        if (joins.endSent && !joins.endShown) return true
        if (joins.endShown && player.isPlaying) {
            seekToStart(player, 0)
            return true
        }
        val lanes = pool ?: LanePool(player).also { pool = it }
        lanes.plan(joins.ranges, joins.decoding, decodedMs)
        return false
    }

    /** A seek landed on [targetMs]: which range that is, or on to the next one. */
    fun afterSeek(targetMs: Long) {
        if (!joins.active) return
        decodedMs = targetMs
        joins.endSent = false
        joins.endShown = false
        relocate(player, targetMs)
    }

    /**
     * Before a grab: a freeze being decoded sends its one picture — the one already on
     * screen, after a seek into its hold — and the loop goes straight on to the next range,
     * while the presenter holds the frame.
     */
    suspend fun beforeGrab(grabber: FFmpegFrameGrabber, generation: Int, hdr: HdrInfo?): FFmpegFrameGrabber? {
        val range = joins.ranges.getOrNull(joins.decoding)?.takeIf { it.isFreeze } ?: return null
        val frame = player.currentFrame
        if (!joins.freezeSent && frame != null) {
            channel.send(VideoFrame(frame, range.fromMs, generation, hdr, player.currentFrameId, joins.decoding, joins.heldMs))
        }
        joins.freezeSent = true
        return handOff(grabber, null, generation, hdr)
    }

    /** A decoded [picture] at [tsMs]: the grabber to go on with once it is a join, else null. */
    suspend fun step(grabber: FFmpegFrameGrabber, picture: Frame, tsMs: Long, generation: Int, hdr: HdrInfo?): FFmpegFrameGrabber? {
        val range = joins.ranges.getOrNull(joins.decoding) ?: return null
        decodedMs = tsMs
        if (tsMs < range.toMs) return null
        return handOff(grabber, picture, generation, hdr, tsMs)
    }

    /** The file ended inside the playing range — a join all the same. */
    suspend fun pastEnd(grabber: FFmpegFrameGrabber, generation: Int, hdr: HdrInfo?): FFmpegFrameGrabber =
        handOff(grabber, null, generation, hdr)

    suspend fun close() {
        pool?.closeAll()
        pool = null
    }

    /**
     * Into the range after the one decoding: straight on when it [continues], from a parked
     * lane when one is ready — waiting for one still rolling, which is never longer than
     * seeking afresh — and by seeking this grabber only when no lane holds it.
     */
    private suspend fun handOff(
        grabber: FFmpegFrameGrabber,
        picture: Frame?,
        generation: Int,
        hdr: HdrInfo?,
        tsMs: Long = decodedMs
    ): FFmpegFrameGrabber {
        val ranges = joins.ranges
        val next = joins.decoding + 1
        val range = ranges.getOrNull(next) ?: return grabber.also { sendEnd(generation) }
        if (picture != null && continues(ranges[joins.decoding], range)) {
            joins.decoding = next
            send(picture, tsMs, generation, hdr, next)
            return grabber
        }
        val lane = pool?.laneFor(next, range.fromMs)?.takeIf { it.awaitParked() }
        val landed = lane?.trade(grabber, player.pictureFilter) ?: landHere(player, grabber, range)
            ?: return grabber.also { sendEnd(generation) }
        joins.decoding = next
        joins.freezeSent = range.isFreeze
        send(landed.picture, landed.tsMs, generation, hdr, next)
        landed.picture.close()
        decodedMs = landed.tsMs
        pacer.rebase()
        return if (range.isFreeze) handOff(landed.grabber, null, generation, hdr) else landed.grabber
    }

    private suspend fun send(picture: Frame, tsMs: Long, generation: Int, hdr: HdrInfo?, joinIndex: Int) {
        val paced = pacer.convert(picture)
        channel.send(VideoFrame(paced.bitmap, tsMs, generation, hdr, paced.id, joinIndex))
    }

    private suspend fun sendEnd(generation: Int) {
        if (joins.endSent) return
        joins.endSent = true
        channel.send(VideoFrame(ImageBitmap(1, 1), END_OF_EDIT_TS, generation))
    }
}
