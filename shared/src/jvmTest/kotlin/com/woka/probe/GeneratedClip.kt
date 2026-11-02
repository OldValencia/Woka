package com.woka.probe

import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import java.io.File
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

internal const val CLIP_FPS = 30
private const val SAMPLE_RATE = 48_000
private const val CHANNELS = 2

/** `AV_PIX_FMT_YUV420P`, as in `FfmpegEncodeProbe`. */
private const val YUV420P = 0

/**
 * A recorder for an H.264 + AAC mp4 at [CLIP_FPS], with a keyframe every [gop] frames.
 * [apply] runs last, so a trial's bitrate control overrides nothing it should not.
 */
internal fun clipRecorder(
    target: File,
    codec: String,
    width: Int,
    height: Int,
    gop: Int,
    apply: (FFmpegFrameRecorder) -> Unit = {}
): FFmpegFrameRecorder {
    val recorder = FFmpegFrameRecorder(target, width, height, CHANNELS)
    recorder.format = "mp4"
    recorder.videoCodecName = codec
    recorder.pixelFormat = YUV420P
    recorder.frameRate = CLIP_FPS.toDouble()
    recorder.gopSize = gop
    recorder.audioCodec = AV_CODEC_ID_AAC
    recorder.sampleRate = SAMPLE_RATE
    recorder.audioBitrate = 128_000
    apply(recorder)
    return recorder
}

/**
 * [frames] frames of a diagonal pattern that **moves** — two pixels a frame — under a
 * light grain, with a 440 Hz tone beside it. Moving so a GOP has real P-frames in it;
 * grained so a bitrate target has something to spend; not pure noise, because noise
 * overruns every rate control and makes 1 Mbit/s and 4 Mbit/s come out identical.
 *
 * The `Frame` is built by hand and lives for the whole recording, so ME-181's
 * freed-converter trap has nothing to bite.
 */
internal fun writeClip(recorder: FFmpegFrameRecorder, width: Int, height: Int, frames: Int) {
    val frame = Frame(width, height, Frame.DEPTH_UBYTE, 3)
    val random = Random(frames)
    val row = ByteArray(frame.imageStride)
    val grain = ByteArray(frame.imageStride)
    val perFrame = SAMPLE_RATE / CLIP_FPS
    val samples = ShortArray(perFrame * CHANNELS)
    repeat(frames) { index ->
        val buffer = frame.image[0] as ByteBuffer
        repeat(height) { line ->
            random.nextBytes(grain)
            for (x in 0 until width * 3) {
                val base = (x / 3 + line + index * 2) and 0xFF
                row[x] = (base + (grain[x].toInt() shr 5)).toByte()
            }
            buffer.position(line * frame.imageStride)
            buffer.put(row, 0, minOf(row.size, buffer.remaining()))
        }
        buffer.rewind()
        recorder.record(frame)
        repeat(perFrame) { sample ->
            val t = (index * perFrame + sample).toDouble() / SAMPLE_RATE
            val level = (sin(2 * PI * 440 * t) * 6000).toInt().toShort()
            samples[sample * 2] = level
            samples[sample * 2 + 1] = level
        }
        recorder.recordSamples(SAMPLE_RATE, CHANNELS, ShortBuffer.wrap(samples))
    }
}

/**
 * A fixture the corpus does not have: **more than one keyframe**. Every committed video
 * opens on its only keyframe, so a seek to a third in lands back on zero and a ranged
 * copy proves nothing about cutting mid-file.
 */
internal fun generateClip(target: File, codec: String, seconds: Int, gop: Int, format: String = "mp4") {
    val recorder = clipRecorder(target, codec, 640, 360, gop) { it.videoBitrate = 1_500_000 }
    recorder.format = format
    recorder.start()
    try {
        writeClip(recorder, 640, 360, seconds * CLIP_FPS)
    } finally {
        recorder.stop()
        recorder.release()
    }
}
