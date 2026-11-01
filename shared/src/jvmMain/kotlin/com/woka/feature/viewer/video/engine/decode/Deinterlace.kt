package com.woka.feature.viewer.video.engine.decode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.sync.originUs
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVFrame.AV_FRAME_FLAG_INTERLACED
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/**
 * Deinterlacing in the player (VF-102): whether this file's frames are interlaced,
 * and the [DeinterlaceGraph] that turns them into whole pictures once they are.
 *
 * **Decided by the decoded frame's own flag, never the stream's `field_order`.**
 * Measured 2026-11-01 (feature doc §7): `field_order` names the wrong parity on two
 * DVD samples, calls a film interlaced and says `unknown` on an interlaced MPEG-2.
 * The flag reaches the RGBA frame the grabber hands out — JavaCV copies the
 * decoder's frame properties onto it — so a progressive file is only ever looked
 * at, and keeps its decode path untouched.
 *
 * Confined to the decoder thread, except [enabled] and [isInterlaced].
 */
internal class Deinterlace {
    /** The user's Auto (true) or Off; Off leaves an interlaced file combed. */
    @Volatile
    var enabled = true

    /** True once a frame of this file was flagged interlaced. Drives the ⚙ row. */
    var isInterlaced by mutableStateOf(false)

    @Volatile
    private var seen = false
    private var graph: DeinterlaceGraph? = null
    private var grabber: FFmpegFrameGrabber? = null

    /** Whether the next grabber opens RAW for the graph. */
    val active: Boolean get() = enabled && seen

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

    /** A fresh graph for a just-started [grabber], at its output size. */
    fun open(grabber: FFmpegFrameGrabber) {
        close()
        this.grabber = grabber
        if (!active) return
        val stream = grabber.formatContext.streams(grabber.videoStream)
        graph = DeinterlaceGraph(stream.time_base(), grabber.imageWidth, grabber.imageHeight)
    }

    /** Drops the neighbouring frames `bwdif` holds: every seek and restart. */
    fun reset() {
        grabber?.let { open(it) }
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
}

/**
 * The user's Auto (true) or Off (VF-106). On a file already found interlaced the
 * grabber re-opens in place, so the change shows at once; elsewhere it is only
 * remembered for when a flagged frame turns up. -1 re-opens on the container's
 * default stream when no track was resolved.
 */
fun VideoPlayerState.setDeinterlace(enabled: Boolean) {
    if (deinterlace.enabled == enabled) return
    deinterlace.enabled = enabled
    if (deinterlace.isInterlaced) requestDeinterlaceReopen(-1)
}
