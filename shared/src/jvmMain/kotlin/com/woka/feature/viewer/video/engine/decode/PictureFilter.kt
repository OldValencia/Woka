package com.woka.feature.viewer.video.engine.decode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.probe.clockwiseTurns
import com.woka.feature.viewer.video.engine.sync.originUs
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVFrame.AV_FRAME_FLAG_INTERLACED
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber

/**
 * What the player does to a picture between the decoder and the screen, and the
 * [PictureGraph] that does it: deinterlacing (VF-102) and the display rotation
 * (VF-105). A file that needs neither keeps JavaCV's own RGBA path untouched.
 *
 * **Interlacing is decided by the decoded frame's own flag, never the stream's
 * `field_order`.** Measured 2026-11-01 (feature doc §7): `field_order` names the
 * wrong parity on two DVD samples, calls a film interlaced and says `unknown` on an
 * interlaced MPEG-2. The flag reaches the RGBA frame too — JavaCV copies the
 * decoder's frame properties onto it.
 *
 * **Rotation comes from the stream's display matrix**, which JavaCV reports and never
 * applies — a phone's portrait video arrived on its side (VF-105).
 *
 * Confined to the decoder thread, except the `@Volatile` fields and [isInterlaced].
 */
internal class PictureFilter {
    /** The user's Auto (true) or Off; Off leaves an interlaced file combed. */
    @Volatile
    var deinterlaceEnabled = true

    /** True once a frame of this file was flagged interlaced. Drives the ⚙ row. */
    var isInterlaced by mutableStateOf(false)

    /** Clockwise quarter turns the selected stream is shown with; read on open. */
    @Volatile
    var turns = 0
        private set

    @Volatile
    private var seen = false
    private var graph: PictureGraph? = null
    private var grabber: FFmpegFrameGrabber? = null
    private var outWidth = 0
    private var outHeight = 0

    private val deinterlacing: Boolean get() = deinterlaceEnabled && seen

    /** Whether the next grabber opens RAW for the graph. */
    val active: Boolean get() = deinterlacing || turns != 0

    /** Whether this grabber's frames go through the graph — RAW frames only. */
    val filtering: Boolean get() = graph != null

    /** True the first time a frame carries the interlaced flag. */
    fun observe(frame: Frame): Boolean {
        if (seen) return false
        val flags = (frame.opaque as? AVFrame)?.flags() ?: return false
        if (flags and AV_FRAME_FLAG_INTERLACED == 0) return false
        seen = true
        return true
    }

    /**
     * A fresh graph for a just-started [grabber], scaling to [outWidth]×[outHeight] —
     * the render-scale's size, already in the turned orientation, 0 for the stream's
     * own. Not the grabber's `imageWidth`: on a RAW grabber with no size set that reads
     * back the stored width, and a turned picture was scaled flat again (2026-11-01).
     *
     * True when the grabber was opened on the plain path but its stream needs the
     * graph — a rotated file on its first open — so the caller re-opens it RAW.
     */
    fun open(grabber: FFmpegFrameGrabber, outWidth: Int, outHeight: Int): Boolean {
        close()
        this.grabber = grabber
        this.outWidth = outWidth
        this.outHeight = outHeight
        turns = grabber.clockwiseTurns
        if (!active) return false
        if (grabber.imageMode != FrameGrabber.ImageMode.RAW) return true
        val stream = grabber.formatContext.streams(grabber.videoStream)
        graph = PictureGraph(stream.time_base(), outWidth, outHeight, deinterlacing, turns)
        return false
    }

    /** Drops the neighbouring frames `bwdif` holds: every seek and restart. */
    fun reset() {
        grabber?.let { open(it, outWidth, outHeight) }
    }

    /** The picture for a RAW [frame], or null while the graph waits for the next one. */
    fun process(frame: Frame): Frame? = graph?.process(frame.opaque as AVFrame)

    /** The last picture, held back at end of stream. */
    fun drain(): Frame? = graph?.drain()

    /** [picture]'s time on the media timeline, in µs — the graph's, not the grabber's. */
    fun mediaTimeUs(picture: Frame): Long =
        picture.timestamp - (grabber?.formatContext?.originUs ?: 0L)

    fun close() {
        graph?.close()
        graph = null
    }

    /** A decoder lane's filter takes the player's settings before its first open (ME-232). */
    fun mirror(main: PictureFilter) {
        deinterlaceEnabled = main.deinterlaceEnabled
        seen = main.seen
    }

    /**
     * Trades graphs with a lane's filter at a hand-off (ME-232): each graph stays with the
     * grabber it was built on, and the grabbers are what changed hands.
     */
    fun swapGraphWith(other: PictureFilter) {
        graph = other.graph.also { other.graph = graph }
        grabber = other.grabber.also { other.grabber = grabber }
        outWidth = other.outWidth.also { other.outWidth = outWidth }
        outHeight = other.outHeight.also { other.outHeight = outHeight }
    }
}

/**
 * The user's Auto (true) or Off (VF-106). On a file already found interlaced the
 * grabber re-opens in place, so the change shows at once; elsewhere it is only
 * remembered for when a flagged frame turns up. -1 re-opens on the container's
 * default stream when no track was resolved.
 */
fun VideoPlayerState.setDeinterlace(enabled: Boolean) {
    if (pictureFilter.deinterlaceEnabled == enabled) return
    pictureFilter.deinterlaceEnabled = enabled
    if (pictureFilter.isInterlaced) requestPictureReopen(-1)
}
