package com.woka.feature.editor.engine.video

import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.viewer.video.engine.probe.requireSupportedVideo
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File

/**
 * A header-only check on explicit Done, on IO: an unsupported preview must not let an
 * unseen edit change the original. No RGBA bitmap is produced or cached by this check.
 * Revert remains available to remove existing edits from such a file.
 */
internal fun videoWriteFailure(file: File): EditSaveResult.Failed? {
    val grabber = FFmpegFrameGrabber(file).apply {
        imageMode = FrameGrabber.ImageMode.RAW
        audioStream = Int.MAX_VALUE
        setOption("probesize", "2000000")
        setOption("analyzeduration", "1000000")
        setVideoOption("threads", "2")
    }
    return try {
        grabber.start()
        grabber.requireSupportedVideo()
        null
    } catch (failure: Exception) {
        EditSaveResult.Failed(failure.message ?: "This video's format could not be checked. Nothing was saved.")
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}
