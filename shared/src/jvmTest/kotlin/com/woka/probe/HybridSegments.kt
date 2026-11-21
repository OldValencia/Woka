package com.woka.probe

import org.bytedeco.ffmpeg.global.avcodec.av_packet_rescale_ts
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.av_make_q
import org.bytedeco.ffmpeg.global.avutil.av_rescale_q
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/** TEMPORARY (ME-295): bounded external reference commands; logs remain beside each output. */
internal fun hybridCommand(dir: File, label: String, vararg args: String): File {
    val log = File(dir, "$label.log")
    val process = ProcessBuilder(listOf("ffmpeg", "-hide_banner", "-nostdin", "-y") + args)
        .directory(dir).redirectErrorStream(true).redirectOutput(log).start()
    if (!process.waitFor(90, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        error("$label exceeded 90 s; $log")
    }
    check(process.exitValue() == 0) { "$label exit=${process.exitValue()}; $log" }
    return log
}

/** Fixed GOPs put both copy boundaries on random-access frames before hybrid is attempted. */
internal fun hybridSource(dir: File, kind: String): File {
    val source = File(dir, "source.mp4")
    if (kind == "openh264") {
        generateClip(source, "libopenh264", seconds = 6, gop = 30)
    } else {
        val hevc = kind == "hevc10"
        val codec = if (hevc) "libx265" else "libx264"
        val options = if (hevc) listOf("-x265-params", "keyint=60:min-keyint=60:scenecut=0:open-gop=0:pools=2:frame-threads=2:log-level=error")
        else listOf("-x264-params", "keyint=60:min-keyint=60:scenecut=0:open-gop=0:bframes=${if (kind == "x264-baseline") 0 else 3}") +
            if (kind == "x264-baseline") listOf("-profile:v", "baseline") else emptyList()
        hybridCommand(dir, "generate", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=30:duration=6",
            "-c:v", codec, "-preset", "fast", "-pix_fmt", if (hevc) "yuv420p10le" else "yuv420p",
            *options.toTypedArray(), source.path)
    }
    return source
}

/** The production encoder's H.264 settings, changing pixels to prevent a false copy test. */
internal fun hybridEncode(source: File, target: File) = withGrabber(source) { grabber ->
    val recorder = FFmpegFrameRecorder(target, grabber.imageWidth, grabber.imageHeight, 0)
    recorder.format = "mp4"
    recorder.videoCodecName = "libopenh264"
    recorder.pixelFormat = 0
    recorder.frameRate = grabber.frameRate
    recorder.gopSize = (grabber.frameRate * 2).toInt()
    recorder.videoBitrate = 1_500_000
    recorder.start()
    try {
        while (true) {
            val frame = grabber.grabImage() ?: break
            val pixels = frame.image[0] as ByteBuffer
            for (index in 0 until pixels.limit()) pixels.put(index, pixels.get(index).toInt().inv().toByte())
            recorder.record(frame)
        }
    } finally {
        recorder.stop()
        recorder.release()
    }
}

/**
 * One output stream with the first segment's global parameters, as JavaCV's copy API exposes.
 * Rebasing uses intended segment durations; DTS is never silently dropped to hide a bad join.
 */
internal fun hybridPackets(segments: List<File>, target: File) = withGrabber(segments.first()) { first ->
    val recorder = FFmpegFrameRecorder(target, first.imageWidth, first.imageHeight, 0)
    recorder.format = "mp4"
    recorder.start(first.formatContext)
    val base = first.formatContext.streams(first.videoStream).time_base()
    val micros = av_make_q(1, 1_000_000)
    var offsetUs = 0L
    var outputDelay: Long? = null
    try {
        for (segment in segments) withGrabber(segment) { grabber ->
            val context = grabber.formatContext
            val video = grabber.videoStream
            val inputBase = context.streams(video).time_base()
            val startUs = startOf(grabber)
            val shift = av_rescale_q(offsetUs - startUs, micros, base)
            var segmentDelay: Long? = null
            while (true) {
                val packet = grabber.grabPacket() ?: break
                if (packet.stream_index() != video) continue
                av_packet_rescale_ts(packet, inputBase, base)
                if (segmentDelay == null) {
                    segmentDelay = packet.pts() - packet.dts()
                    if (outputDelay == null) outputDelay = segmentDelay
                }
                if (packet.pts() != AV_NOPTS_VALUE) packet.pts(packet.pts() + shift)
                // ME-295: equalise decoder lead without dropping the first B-packets at a join.
                if (packet.dts() != AV_NOPTS_VALUE) packet.dts(packet.dts() + shift + segmentDelay - outputDelay!!)
                packet.stream_index(first.videoStream)
                recorder.recordPacket(packet)
            }
            offsetUs += grabber.lengthInTime
        }
    } finally {
        recorder.stop()
        recorder.release()
    }
}
