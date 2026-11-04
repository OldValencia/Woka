package com.woka.feature.editor.engine.video.render

import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File

/**
 * How a render's encoder is opened against its source (ME-292): libopenh264 at the source's
 * own density, the source's frame rate and rotation, AAC when there is sound. Split out of
 * `FrameRender` when the edit's sound (ME-240) took that file past the size mandate.
 */

/** `AV_PIX_FMT_YUV420P` — what every H.264 player decodes. */
private const val PIX_FMT_YUV420P = 0

/** Where the source does not say how fast it is, nor how dense. */
private const val FALLBACK_FPS = 30.0
private const val FALLBACK_BITRATE = 8_000_000

/** A light edit must come out no larger (§0), and a tiny source must still look like video. */
private const val MIN_BITRATE = 1_000_000
private const val MAX_BITRATE = 60_000_000

private const val AUDIO_BITRATE = 192_000

internal fun encodeRecorder(grabber: FFmpegFrameGrabber, target: File, format: String, size: EvenSize, bitrate: Int) =
    FFmpegFrameRecorder(target, size.width, size.height, grabber.audioChannels).apply {
        this.format = format
        videoCodecName = "libopenh264"
        pixelFormat = PIX_FMT_YUV420P
        frameRate = grabber.frameRate.takeIf { it > 0.0 } ?: FALLBACK_FPS
        gopSize = (frameRate * 2).toInt().coerceAtLeast(1)
        videoBitrate = bitrate
        // A phone film is stored on its side and turned by its matrix; the frames decoded
        // here are the stored ones, so the matrix has to travel or the render lies down.
        setDisplayRotation(grabber.displayRotation)
        if (grabber.audioChannels > 0) {
            audioCodec = AV_CODEC_ID_AAC
            sampleRate = grabber.sampleRate
            audioBitrate = AUDIO_BITRATE
        }
        start()
    }

/**
 * The source's video bitrate, or — where the container does not record one, as Matroska
 * usually does not — the whole file's average, which overstates it by the audio's share
 * and errs towards quality.
 */
internal fun sourceBitrate(grabber: FFmpegFrameGrabber, source: File): Int {
    val declared = grabber.videoBitrate
    if (declared > 0) return declared.coerceIn(MIN_BITRATE, MAX_BITRATE)
    val seconds = grabber.lengthInTime / 1_000_000.0
    if (seconds <= 0.0) return FALLBACK_BITRATE
    return (source.length() * 8 / seconds).toInt().coerceIn(MIN_BITRATE, MAX_BITRATE)
}
