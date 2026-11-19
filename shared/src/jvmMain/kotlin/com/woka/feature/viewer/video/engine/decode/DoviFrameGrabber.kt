package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.probe.isDoviProfile5
import com.woka.feature.viewer.video.engine.probe.requireSupportedVideo
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber.ImageMode
import java.io.File

/** DV5-102: one colour path for every FreshFrameGrabber consumer, including RAW graphs. */
internal open class DoviFrameGrabber(file: File, private val inspectRaw: Boolean) : FFmpegFrameGrabber(file) {
    private var p5: P5GrabberState? = null
    internal var p5Playback: Boolean = false
    protected val doviColourActive: Boolean get() = p5 != null

    @Synchronized
    override fun start(findStreamInfo: Boolean) {
        if (p5 != null) return
        val request = P5PictureRequest(super.getImageMode(), super.getImageWidth(), super.getImageHeight(), super.getPixelFormat())
        super.start(findStreamInfo)
        if (inspectRaw || !isDoviProfile5()) return
        requireSupportedVideo()
        val source = formatContext.streams(videoStream).codecpar()
        val decoderThreads = videoOptions["threads"]
        // DV5-104: 4K60 after GPU colour needs eight decode threads (81.6 vs 62.6 FPS).
        val moreThreads = p5Playback && decoderThreads == "4" && frameRate >= 50 &&
            source.width().toLong() * source.height() >= 3840L * 2160 &&
            Runtime.getRuntime().availableProcessors() >= 8
        // JavaCV frees COLOR image_ptr with av_free, but RAW image_ptr belongs to the
        // decoder. Reopen before changing modes; never relabel a live COLOR buffer RAW.
        try {
            super.release()
            super.setImageMode(ImageMode.RAW)
            super.setImageWidth(0)
            super.setImageHeight(0)
            if (moreThreads) setVideoOption("threads", "8")
            super.start(findStreamInfo)
            p5 = P5GrabberState(request, super.getImageWidth(), super.getImageHeight())
        } catch (failure: Throwable) {
            try {
                super.release()
            } finally {
                request.restore(this)
            }
            throw failure
        } finally {
            if (moreThreads) setVideoOption("threads", decoderThreads)
        }
    }

    @Synchronized
    override fun grabFrame(doAudio: Boolean, doVideo: Boolean, doProcessing: Boolean, keyFrames: Boolean, doData: Boolean): Frame? {
        val active = p5 ?: return super.grabFrame(doAudio, doVideo, doProcessing, keyFrames, doData)
        // Seek's unprocessed reads need a valid RAW AVFrame too, but no colour work.
        val raw = super.grabFrame(doAudio, doVideo, doProcessing || doVideo, keyFrames, doData) ?: return null
        return if (raw.image != null && doProcessing) active.convert(raw) else raw
    }

    override fun getImageMode(): ImageMode = p5?.request?.mode ?: super.getImageMode()
    override fun getImageWidth(): Int = p5?.width ?: super.getImageWidth()
    override fun getImageHeight(): Int = p5?.height ?: super.getImageHeight()
    override fun getPixelFormat(): Int = p5?.format ?: super.getPixelFormat()

    override fun setImageMode(mode: ImageMode) {
        val active = p5
        if (active == null) super.setImageMode(mode) else active.request = active.request.copy(mode = mode)
    }

    override fun setImageWidth(width: Int) {
        val active = p5
        if (active == null) super.setImageWidth(width) else active.request = active.request.copy(width = width)
    }

    override fun setImageHeight(height: Int) {
        val active = p5
        if (active == null) super.setImageHeight(height) else active.request = active.request.copy(height = height)
    }

    override fun setPixelFormat(format: Int) {
        val active = p5
        if (active == null) super.setPixelFormat(format) else active.request = active.request.copy(format = format)
    }

    @Synchronized
    override fun release() {
        val active = p5
        p5 = null
        try {
            active?.close()
        } finally {
            try {
                super.release()
            } finally {
                active?.request?.restore(this)
            }
        }
    }
}
