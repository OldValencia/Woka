package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.probe.requireSupportedVideo
import com.woka.feature.viewer.video.engine.sync.originUs
import kotlinx.coroutines.CancellationException
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.javacv.Frame
import java.io.File

/**
 * Viewer-only adapter; JavaCV retains headers and an unused software codec for fallback (EK-002).
 * Hardware owns its codec, frames and packets, borrowing the public demux context. Reflection
 * would couple resource ownership to JavaCV private fields and cannot be used here.
 * A failed device is retired for this open; seeking software resumes at the next media time.
 */
internal class AcceleratedFrameGrabber(
    file: File,
    private val alive: () -> Boolean = { true }
) : FreshFrameGrabber(file) {
    private var decoder: CudaVideoDecoder? = null
    private var converter: ThreadedVideoConverter? = null
    internal val hardwareActive: Boolean get() = decoder != null
    internal var accelerationFailure: String? = null
        private set

    @Synchronized
    override fun start(findStreamInfo: Boolean) {
        super.start(findStreamInfo)
        if (!canAccelerateVideo()) return
        requireSupportedVideo()
        try {
            decoder = CudaVideoDecoder(formatContext, videoStream, alive)
            converter = ThreadedVideoConverter(imageWidth, imageHeight)
            accelerationFailure = null
        } catch (failure: RuntimeException) {
            dropHardware()
            recordFailure(failure)
        }
    }

    @Synchronized
    override fun grabFrame(
        doAudio: Boolean,
        doVideo: Boolean,
        doProcessing: Boolean,
        keyFrames: Boolean,
        doData: Boolean
    ): Frame? {
        val active = decoder
        if (active != null) {
            val resumeUs = (timestamp - formatContext.originUs + 1).coerceAtLeast(0L)
            try {
                check(doVideo && !keyFrames && !doData && !(doAudio && hasAudio())) {
                    "Requested grab mode needs software"
                }
                val picture = active.next() ?: return null
                val frame = checkNotNull(converter).convert(picture, active.timestampUs)
                timestamp = frame.timestamp
                frame.streamIndex = videoStream
                lastPicture = frame.opaque as AVFrame
                return frame
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: RuntimeException) {
                fallback(failure, resumeUs)
            }
        }
        return super.grabFrame(doAudio, doVideo, doProcessing, keyFrames, doData)
    }

    @Synchronized
    override fun setTimestamp(timestamp: Long) {
        val active = decoder
        if (active == null) {
            super.setTimestamp(timestamp)
            return
        }
        try {
            active.seek(timestamp)
            this.timestamp = active.timestampUs
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RuntimeException) {
            fallback(failure, timestamp)
        }
    }

    override fun setTimestamp(timestamp: Long, checkFrame: Boolean) {
        if (decoder == null) super.setTimestamp(timestamp, checkFrame) else setTimestamp(timestamp)
    }

    override fun setVideoTimestamp(timestamp: Long) {
        if (decoder == null) super.setVideoTimestamp(timestamp) else setTimestamp(timestamp)
    }

    private fun fallback(failure: RuntimeException, mediaUs: Long) {
        dropHardware()
        recordFailure(failure)
        super.setTimestamp(mediaUs)
    }

    private fun recordFailure(failure: RuntimeException) {
        accelerationFailure = failure.message ?: failure.javaClass.simpleName
        System.err.println("Video acceleration: software fallback: $accelerationFailure")
    }

    private fun dropHardware() {
        if (decoder != null || converter != null) lastPicture = null
        converter?.close()
        converter = null
        decoder?.close()
        decoder = null
    }

    @Synchronized
    override fun release() {
        dropHardware()
        super.release()
    }
}
