package com.woka.feature.viewer.video.engine.join

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.decode.PictureFilter
import com.woka.feature.viewer.video.engine.decode.openLaneGrabber
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber

/**
 * A decoder lane's grabber and what it was opened for — the size, the stream and the mode —
 * so the pool can tell a lane that no longer matches the player's (ME-232). Split out of
 * `DecoderLane` when letting a lane go without waiting took that file past the size mandate.
 */
internal class LaneGrabber(private val player: VideoPlayerState) {

    var grabber: FFmpegFrameGrabber? = null
        private set

    private var width = -1
    private var stream = -1
    private var raw = false

    /**
     * Opened at a size, on a stream or **in a mode** the player no longer uses: a lane opened
     * RGBA before the first frame showed the file interlaced handed the player a combed picture
     * with no graph at its first join (1.0.6 review).
     */
    val isStale: Boolean
        get() = grabber != null &&
            (width != player.renderScale.decodeWidth || stream != (player.tracks.selectedVideoIndex ?: stream) ||
                raw != player.pictureFilter.active)

    /** The grabber, opened on first use behind [filter] with the player's settings. */
    fun openedFor(filter: PictureFilter): FFmpegFrameGrabber {
        grabber?.let { return it }
        val lane = player.openLaneGrabber()
        // A lane that cannot open is released here, not left holding its native context.
        try {
            lane.start()
        } catch (failure: Exception) {
            runCatching { lane.release() }
            throw failure
        }
        filter.mirror(player.pictureFilter)
        filter.open(lane, player.renderScale.decodeWidth, player.renderScale.decodeHeight)
        adopt(lane)
        return lane
    }

    /** Takes over [outgoing] — the player's, at a trade — as this lane's grabber. */
    fun adopt(outgoing: FFmpegFrameGrabber) {
        grabber = outgoing
        width = player.renderScale.decodeWidth
        stream = outgoing.videoStream
        raw = outgoing.imageMode == FrameGrabber.ImageMode.RAW
    }

    fun close() {
        grabber?.let {
            runCatching {
                it.stop()
                it.release()
            }
        }
        grabber = null
    }
}
