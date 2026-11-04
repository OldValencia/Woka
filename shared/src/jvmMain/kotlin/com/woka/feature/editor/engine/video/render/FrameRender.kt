package com.woka.feature.editor.engine.video.render

import com.woka.feature.editor.tools.trim.TrimClip
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import java.io.File

/** `AV_PIX_FMT_YUV420P` — what every H.264 player decodes. */
private const val PIX_FMT_YUV420P = 0

/** Where the source does not say how fast it is, nor how dense. */
private const val FALLBACK_FPS = 30.0
private const val FALLBACK_BITRATE = 8_000_000

/** A light edit must come out no larger (§0), and a tiny source must still look like video. */
private const val MIN_BITRATE = 1_000_000
private const val MAX_BITRATE = 60_000_000

private const val AUDIO_BITRATE = 192_000

/**
 * The encode path of a render (ME-292): every clip decoded, drawn through its picture when
 * there is pixel work, and encoded once into a fresh H.264 + AAC stream.
 *
 * **libopenh264**, because ME-290 measured it: the LGPL build has no x264, NVENC needs a
 * newer driver than the machine has, QSV refuses the session, and of the two that open,
 * openh264 lands on the bitrate it is given (3998 kbps for 4M) where Media Foundation
 * overshoots by 14 %. No CRF exists here, so the quality target is a **bitrate measured off
 * the source** (`render.md` §3.1): the same density, so a light edit comes out the same
 * size per second and therefore no larger than the source by exactly what was cut.
 *
 * Timestamps are the timeline's: each clip's frames are placed at where the clip starts
 * in the result, and audio is appended in order, so the two stay level clip by clip.
 */
internal fun encodeClips(
    source: File,
    target: File,
    format: String,
    clips: List<TrimClip>,
    /** Each clip's picture (ME-215) — one frame size for all, since geometry is the film's. */
    pictureOf: (TrimClip) -> FramePicture?,
    alive: () -> Boolean,
    progress: (Float) -> Unit
) {
    val grabber = FFmpegFrameGrabber(source).apply { pixelFormat = AV_PIX_FMT_RGBA }
    try {
        grabber.start()
        val picture = clips.firstNotNullOfOrNull(pictureOf)
        val size = picture?.outputSize ?: evenSize(grabber.imageWidth, grabber.imageHeight)
        val recorder = encodeRecorder(grabber, target, format, size, sourceBitrate(grabber, source))
        try {
            recordClips(grabber, recorder, clips, pictureOf, alive, progress)
        } finally {
            recorder.stop()
            recorder.release()
        }
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}

private fun encodeRecorder(grabber: FFmpegFrameGrabber, target: File, format: String, size: EvenSize, bitrate: Int) =
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

private fun recordClips(
    grabber: FFmpegFrameGrabber,
    recorder: FFmpegFrameRecorder,
    clips: List<TrimClip>,
    pictureOf: (TrimClip) -> FramePicture?,
    alive: () -> Boolean,
    progress: (Float) -> Unit
) {
    val start = containerStartUs(grabber)
    val totalUs = clips.sumOf { it.lengthMs } * 1000L
    var baseUs = 0L
    for (clip in clips) {
        val picture = pictureOf(clip)
        val fromUs = clip.fromMs * 1000L
        val untilUs = clip.toMs * 1000L
        grabber.setTimestamp(fromUs)
        while (alive()) {
            val frame = grabber.grab() ?: break
            val atUs = frame.timestamp - start
            if (atUs >= untilUs && frame.image != null) break
            if (atUs < fromUs || atUs >= untilUs) continue
            if (frame.image != null) {
                recorder.timestamp = baseUs + (atUs - fromUs)
                recorder.record(picture?.draw(frame) ?: frame, AV_PIX_FMT_RGBA)
                if (totalUs > 0) progress((baseUs + atUs - fromUs).toFloat() / totalUs)
            } else if (frame.samples != null) {
                recorder.record(frame)
            }
        }
        if (!alive()) return
        baseUs += untilUs - fromUs
    }
}

/**
 * The source's video bitrate, or — where the container does not record one, as Matroska
 * usually does not — the whole file's average, which overstates it by the audio's share
 * and errs towards quality.
 */
private fun sourceBitrate(grabber: FFmpegFrameGrabber, source: File): Int {
    val declared = grabber.videoBitrate
    if (declared > 0) return declared.coerceIn(MIN_BITRATE, MAX_BITRATE)
    val seconds = grabber.lengthInTime / 1_000_000.0
    if (seconds <= 0.0) return FALLBACK_BITRATE
    return (source.length() * 8 / seconds).toInt().coerceIn(MIN_BITRATE, MAX_BITRATE)
}

/** H.264 in 4:2:0 wants both sides even; a crop to an odd width loses its last column. */
internal data class EvenSize(val width: Int, val height: Int)

internal fun evenSize(width: Int, height: Int) =
    EvenSize((width - width % 2).coerceAtLeast(2), (height - height % 2).coerceAtLeast(2))

/** What a frame looks like after the recipe, at the encoder's size. */
internal interface FramePicture {
    val outputSize: EvenSize
    fun draw(frame: Frame): Frame
}
