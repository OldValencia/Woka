package com.woka.feature.viewer.video.engine.join

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.decode.PictureFilter
import com.woka.feature.viewer.video.engine.decode.openLaneGrabber
import com.woka.feature.viewer.video.engine.sync.landAt
import com.woka.feature.viewer.video.engine.sync.timeUs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/** A lane's picture handed to the decode loop: the grabber to go on with, and where it stands. */
internal class Landed(val grabber: FFmpegFrameGrabber, val picture: Frame, val tsMs: Long)

/**
 * One spare decoder (ME-232): a grabber of its own that seeks to an upcoming range's start
 * **while the player's grabber is still playing**, and parks there holding that picture.
 * At the join the decode loop trades its grabber for this one, so the seek a join used to
 * cost — 0.2–1.9 s on a 4K film (ME-230) — has already been paid.
 *
 * **One picture, not a queue.** The grabber is left just after it, and the next frame
 * decodes in 11–25 ms against a 41 ms frame, so a second parked frame would only hold
 * another 8–33 MB per lane for nothing.
 *
 * Its own [filter], because a picture graph is built on one grabber: at the trade the two
 * graphs change owners with their grabbers. Driven from the decode loop's thread; the
 * pre-roll runs on [Dispatchers.IO] and publishes through the volatile fields.
 */
internal class DecoderLane(private val player: VideoPlayerState) {

    val filter = PictureFilter()

    private var grabber: FFmpegFrameGrabber? = null
    private var width = -1
    private var stream = -1
    private var job: Job? = null

    /** The range this lane is rolling to or parked on, by index and start; -1 when free. */
    @Volatile
    var index = -1
        private set

    @Volatile
    private var fromMs = -1L

    @Volatile
    private var parked: Landed? = null

    /** When the lane last went free — the pool closes one left free too long. */
    var freeSinceMs = System.currentTimeMillis()
        private set

    val isFree: Boolean get() = index < 0

    /** Opened at a size or on a stream the player's grabber no longer uses. */
    val isStale: Boolean
        get() = grabber != null &&
            (width != player.renderScale.decodeWidth || stream != (player.tracks.selectedVideoIndex ?: stream))

    fun holds(rangeIndex: Int, startMs: Long): Boolean = index == rangeIndex && fromMs == startMs

    /** Seeks to [startMs] for range [rangeIndex] in the background, dropping what it held. */
    suspend fun preRoll(rangeIndex: Int, startMs: Long) {
        free()
        index = rangeIndex
        fromMs = startMs
        job = player.scope.launch(Dispatchers.IO) {
            try {
                val lane = grabber ?: open()
                val picture = player.landAt(lane, filter, startMs) { isActive } ?: return@launch
                parked = Landed(lane, picture, filter.timeUs(lane, picture) / 1000L)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A lane that cannot open or seek is a join that seeks, as before ME-232.
                e.printStackTrace()
            }
        }
    }

    /** Waits for a pre-roll in flight; true once the lane holds its picture. */
    suspend fun awaitParked(): Boolean {
        job?.join()
        return parked != null
    }

    /**
     * The parked picture and its grabber, in exchange for the player's [outgoing] one and
     * [playerFilter]'s graph. The lane goes free holding the outgoing grabber, ready for
     * the pool to send somewhere else.
     */
    fun trade(outgoing: FFmpegFrameGrabber, playerFilter: PictureFilter): Landed? {
        val landed = parked ?: return null
        parked = null
        playerFilter.swapGraphWith(filter)
        grabber = outgoing
        width = player.renderScale.decodeWidth
        stream = outgoing.videoStream
        release()
        return landed
    }

    /** Stops any pre-roll and drops its picture; the grabber stays open for the next one. */
    suspend fun free() {
        job?.cancelAndJoin()
        job = null
        parked?.picture?.close()
        parked = null
        release()
    }

    suspend fun close() {
        free()
        filter.close()
        grabber?.let {
            runCatching {
                it.stop()
                it.release()
            }
        }
        grabber = null
    }

    private fun release() {
        if (index >= 0) freeSinceMs = System.currentTimeMillis()
        index = -1
        fromMs = -1L
    }

    private fun open(): FFmpegFrameGrabber {
        val lane = player.openLaneGrabber()
        lane.start()
        filter.mirror(player.pictureFilter)
        filter.open(lane, player.renderScale.decodeWidth, player.renderScale.decodeHeight)
        width = player.renderScale.decodeWidth
        stream = lane.videoStream
        grabber = lane
        return lane
    }
}
