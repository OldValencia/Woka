package com.woka.feature.viewer.video.engine.probe

import com.woka.feature.viewer.video.engine.decode.DoviColourNative
import org.bytedeco.javacv.FFmpegFrameGrabber

internal const val UNSUPPORTED_DOVI_P5 = "Dolby Vision Profile 5 colour processing is unavailable. " +
    "Update or reinstall Woka, or open an HDR10 or SDR version."

/** Expected decode rejection, converted into a failure value at the player boundary. */
internal class UnsupportedVideoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Compatible base layers keep their existing path; P5 requires the checked colour bridge. */
internal fun unsupportedDoviReason(configuration: DoviConfigurationRead, p5Available: Boolean = false): String? = when (configuration) {
    DoviConfigurationRead.Absent -> null
    is DoviConfigurationRead.Present ->
        UNSUPPORTED_DOVI_P5.takeIf { configuration.configuration.profile == 5 && !p5Available }
    is DoviConfigurationRead.Unavailable ->
        "This video's Dolby Vision metadata can't be read. Open an HDR10 or SDR version."
}

/** Called before picture decoding, not on audio-only reads or packet copies. */
internal fun FFmpegFrameGrabber.requireSupportedVideo() {
    val context = formatContext ?: return
    if (videoStream !in 0 until context.nb_streams()) return
    val configuration = readDoviConfiguration(context.streams(videoStream).codecpar())
    val p5 = (configuration as? DoviConfigurationRead.Present)?.configuration?.profile == 5
    val reason = unsupportedDoviReason(configuration, p5 && DoviColourNative.available) ?: return
    val cause = (configuration as? DoviConfigurationRead.Unavailable)?.let { IllegalStateException(it.reason) }
    throw UnsupportedVideoException(reason, cause)
}

internal fun FFmpegFrameGrabber.isDoviProfile5(): Boolean {
    val context = formatContext ?: return false
    if (videoStream !in 0 until context.nb_streams()) return false
    val result = readDoviConfiguration(context.streams(videoStream).codecpar())
    return (result as? DoviConfigurationRead.Present)?.configuration?.profile == 5
}
