package com.woka.probe

import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File

/** The encoders ME-290 asks about, by the name `-c:v`/`-c:a` would take. */
internal val ASKED_ENCODERS = listOf(
    "libopenh264", "h264_mf", "h264_nvenc", "h264_qsv", "h264_amf", "libx264",
    "hevc_mf", "hevc_nvenc", "mpeg4", "libvpx", "libvpx-vp9", "libaom-av1",
    "aac", "libmp3lame", "libopus", "flac", "pcm_s16le", "gif"
)

/** The H.264 encoders worth a real open, in the order the render would prefer them. */
internal val H264_ENCODERS = listOf("libopenh264", "h264_mf", "h264_nvenc", "h264_qsv", "h264_amf", "libx264")

internal fun encoderPresent(name: String): Boolean = avcodec_find_encoder_by_name(name) != null

private const val WIDTH = 1280
private const val HEIGHT = 720
private const val FRAMES = 30

/** One rate control as a label and what it does to the recorder. */
internal typealias Control = Pair<String, (FFmpegFrameRecorder) -> Unit>

/**
 * The rate controls tried per encoder. `videoBitrate` is JavaCV's field (→ `bit_rate`);
 * `b` is the same thing as an `avcodec_open2` option, set over a different
 * `videoBitrate` to show which one wins; the last is the encoder's own rate-control
 * mode — `rc_mode` for openh264, `rate_control` for Media Foundation.
 */
internal fun controlsFor(codec: String): List<Control> = listOf(
    "videoBitrate=1M" to { it.videoBitrate = 1_000_000 },
    "videoBitrate=4M" to { it.videoBitrate = 4_000_000 },
    "b=4M over videoBitrate=1M" to { bitrateMode(it, "b", "4M") },
    when (codec) {
        "libopenh264" -> "1M + rc_mode=bitrate" to { r: FFmpegFrameRecorder -> bitrateMode(r, "rc_mode", "bitrate") }
        "h264_mf" -> "1M + rate_control=cbr" to { r: FFmpegFrameRecorder -> bitrateMode(r, "rate_control", "cbr") }
        // QSV refuses yuv420p outright and names nv12 as what it takes.
        "h264_qsv" -> "1M + pixelFormat=nv12" to { r: FFmpegFrameRecorder -> nv12(r) }
        else -> "1M + (no mode)" to { r: FFmpegFrameRecorder -> r.videoBitrate = 1_000_000 }
    }
)

/** `AV_PIX_FMT_NV12`. */
private const val NV12 = 23

private fun nv12(recorder: FFmpegFrameRecorder) {
    recorder.videoBitrate = 1_000_000
    recorder.pixelFormat = NV12
}

private fun bitrateMode(recorder: FFmpegFrameRecorder, key: String, value: String) {
    recorder.videoBitrate = 1_000_000
    recorder.setVideoOption(key, value)
}

/** One rate control tried against one encoder, and what came out. */
internal data class EncodeTrial(
    val codec: String,
    val control: String,
    val back: Readback? = null,
    val videoKbps: Long = -1,
    val keys: Int = 0,
    val failure: String? = null
) {
    override fun toString(): String = "%-12s %-26s %s".format(
        codec, control,
        failure ?: "open=ok videoKbps=$videoKbps keys=$keys ${back ?: ""}"
    )
}

/**
 * [frames] frames of 1280×720 at 30 fps plus AAC stereo, into an mp4. 30 is the ticket's
 * "does it open and decode"; a rate control needs longer than one GOP to show, so the
 * bitrate rows run 150.
 */
internal fun encodeTrial(codec: String, control: Control, frames: Int = FRAMES): EncodeTrial {
    val label = control.first
    val out = File.createTempFile("probe-h264-$codec", ".mp4").also { it.delete() }
    try {
        val recorder = clipRecorder(out, codec, WIDTH, HEIGHT, CLIP_FPS, control.second)
        val opened = runCatching { recorder.start() }
        if (opened.isFailure) {
            runCatching { recorder.release() }
            return EncodeTrial(codec, label, failure = "start FAILED ${opened.exceptionOrNull()?.brief()}")
        }
        try {
            writeClip(recorder, WIDTH, HEIGHT, frames)
        } finally {
            recorder.stop()
            recorder.release()
        }
        val scan = scanKeys(out)
        val kbps = scan.videoBytes * 8 * CLIP_FPS / frames / 1000
        return EncodeTrial(codec, label, readBack(out), kbps, scan.keyframes)
    } catch (error: Throwable) {
        return EncodeTrial(codec, label, failure = "FAILED ${error.brief()}")
    } finally {
        out.delete()
    }
}
