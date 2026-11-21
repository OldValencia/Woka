package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.join.JoinStep
import com.woka.feature.viewer.video.engine.probe.UnsupportedVideoException
import com.woka.feature.viewer.video.engine.saveResumePosition
import com.woka.feature.viewer.video.engine.writeUiState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import org.bytedeco.javacv.FFmpegFrameGrabber

/** The specific unsupported-format value never becomes the generic damaged-file notice. */
internal suspend fun VideoPlayerState.reportVideoDecodeFailure(failure: Exception) {
    if (failure is UnsupportedVideoException) {
        writeUiState {
            videoFailure.message = failure.message
            currentFrame = null
            currentFrameHdrInfo = null
            isPlaying = false
        }
        currentCoroutineContext().cancelChildren()
        audioDecodeJob?.cancel()
        audioJob?.cancel()
    }
    failure.printStackTrace()
}

/** Existing decoder teardown, extracted from the 150-line loop for the P5 failure path. */
internal suspend fun VideoPlayerState.finishVideoDecoder(
    grabber: FFmpegFrameGrabber,
    joins: JoinStep,
    videoChannel: Channel<VideoFrame>
) {
    // An unsupported video was never watched; opening it must not overwrite its resume record.
    if (videoFailure.message == null) saveResumePosition()
    withContext(NonCancellable) { joins.close() }
    tracksProbed.complete(Unit)
    isReloadingVideo = false
    pictureFilter.close()
    runCatching { grabber.stop() }
    runCatching { grabber.release() }
    videoChannel.close()
    if (currentCoroutineContext().isActive) isPlaying = false
}
