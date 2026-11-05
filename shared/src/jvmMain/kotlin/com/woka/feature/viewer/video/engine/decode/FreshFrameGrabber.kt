package com.woka.feature.viewer.video.engine.decode

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.av_frame_remove_side_data
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.io.File

/**
 * A grabber whose RGBA picture carries **this** frame's side data, not every frame's (ME-237).
 *
 * On the RGBA path JavaCV 1.5.14 calls `av_frame_copy_props(picture_rgb, picture)` on every
 * frame (`FFmpegFrameGrabber.java:1236`), and FFmpeg's copy **appends** the decoder frame's
 * side data without clearing what is there. `picture_rgb` lives as long as the grabber, so it
 * kept them all — measured 2026-11-05 (`VideoHdrPeakProbe`): 19.5 KB a frame on a Dolby
 * Vision + HDR10+ film, 1500 entries after 300 frames, ~1.7 GB of native memory an hour; a
 * phone video's display matrix piled up the same way. `av_frame_get_side_data` walks the list
 * from the front, so it also answered the first frame's HDR10+ for the whole film.
 *
 * So the side data of the picture last returned is cleared **before** the next grab: a caller
 * reads a frame's side data until it grabs again — which is what [lastPicture] is for. A RAW
 * grabber needs none of it, its `picture` is refilled by the decoder, and is only remembered.
 *
 * Every grabber that decodes pictures is one of these; one that reads only the header is not.
 */
internal class FreshFrameGrabber(file: File) : FFmpegFrameGrabber(file) {

    /**
     * The `AVFrame` behind the last picture returned, in either mode, until the next grab.
     * A seek's landed picture is a clone whose `opaque` is no `AVFrame`, which is why the
     * grabber is asked and not the frame (ME-301).
     */
    var lastPicture: AVFrame? = null
        private set

    /** The overload every other `grab*` reaches. */
    @Synchronized
    override fun grabFrame(
        doAudio: Boolean,
        doVideo: Boolean,
        doProcessing: Boolean,
        keyFrames: Boolean,
        doData: Boolean
    ): Frame? {
        if (imageMode != FrameGrabber.ImageMode.RAW) lastPicture?.let { clearSideData(it) }
        val frame = super.grabFrame(doAudio, doVideo, doProcessing, keyFrames, doData)
        if (frame?.image != null) lastPicture = frame.opaque as? AVFrame
        return frame
    }

    override fun stop() {
        lastPicture = null
        super.stop()
    }

    /** `release()` frees the frame too, and a later `start()` must not clear a freed one. */
    override fun release() {
        lastPicture = null
        super.release()
    }

    /** Every type, one at a time: removing a type takes all of its entries with it. */
    private fun clearSideData(frame: AVFrame) {
        while (frame.nb_side_data() > 0) {
            av_frame_remove_side_data(frame, frame.side_data(0).type())
        }
    }
}

/** [FreshFrameGrabber.lastPicture] for a grabber typed as the base class; null for any other. */
internal val FFmpegFrameGrabber.lastPicture: AVFrame?
    get() = (this as? FreshFrameGrabber)?.lastPicture
