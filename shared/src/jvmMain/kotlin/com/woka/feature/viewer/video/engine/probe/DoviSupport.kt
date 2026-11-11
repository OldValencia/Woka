package com.woka.feature.viewer.video.engine.probe

import org.bytedeco.javacv.FFmpegFrameGrabber

internal const val UNSUPPORTED_DOVI_P5 = "Dolby Vision Profile 5 is not supported yet. " +
    "This video needs Dolby Vision colour processing. Open an HDR10 or SDR version."

/** Expected decode rejection, converted into a failure value at the player boundary. */
internal class UnsupportedVideoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A compatibility layer stays as it was; only P5 is explicitly unsupported in this stage. */
internal fun unsupportedDoviReason(configuration: DoviConfigurationRead): String? = when (configuration) {
    DoviConfigurationRead.Absent -> null
    is DoviConfigurationRead.Present ->
        UNSUPPORTED_DOVI_P5.takeIf { configuration.configuration.profile == 5 }
    is DoviConfigurationRead.Unavailable ->
        "This video's Dolby Vision metadata can't be read. Open an HDR10 or SDR version."
}

/** Called before picture decoding, not on audio-only reads or packet copies. */
internal fun FFmpegFrameGrabber.requireSupportedVideo() {
    val context = formatContext ?: return
    if (videoStream !in 0 until context.nb_streams()) return
    val configuration = readDoviConfiguration(context.streams(videoStream).codecpar())
    val reason = unsupportedDoviReason(configuration) ?: return
    val cause = (configuration as? DoviConfigurationRead.Unavailable)?.let { IllegalStateException(it.reason) }
    throw UnsupportedVideoException(reason, cause)
}
