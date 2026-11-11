package com.woka.probe

import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avutil.av_make_q
import org.bytedeco.ffmpeg.global.avutil.av_rescale_q
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File

/**
 * TEMPORARY (ME-245): direct audio-only JavaCV packet copy, including arbitrary cut boundaries.
 * Cut spans select whole packets and concatenate their actual durations; no hidden trimming,
 * silence or re-encoding is used to make a sample-exact claim about stream copy.
 */
internal fun copyAudioPackets(source: File, target: File, ranges: List<Pair<Int, Int>>? = null, selected: Int = -1) {
    val grabber = FFmpegFrameGrabber(source)
    grabber.videoStream = Int.MAX_VALUE
    if (selected >= 0) grabber.audioStream = selected
    try {
        grabber.start()
        val index = grabber.audioStream
        val context = grabber.formatContext
        val base = context.streams(index).time_base()
        val micros = av_make_q(1, 1_000_000)
        val recorder = FFmpegFrameRecorder(target, grabber.audioChannels)
        recorder.format = "ipod"
        try {
            recorder.start(context)
            if (ranges == null) {
                while (true) {
                    val packet = grabber.grabPacket() ?: break
                    if (packet.stream_index() == index) recorder.recordPacket(packet)
                }
            } else {
                var next = 0L
                for ((fromMs, toMs) in ranges) {
                    check(av_seek_frame(context, -1, fromMs * 1000L, AVSEEK_FLAG_BACKWARD) >= 0)
                    var first: Long? = null
                    val offset = next
                    while (true) {
                        val packet = grabber.grabPacket() ?: break
                        if (packet.stream_index() != index) continue
                        val time = av_rescale_q(packet.pts(), base, micros)
                        if (time < fromMs * 1000L) continue
                        if (time >= toMs * 1000L) break
                        if (first == null) first = packet.pts()
                        val duration = packet.duration()
                        val pts = packet.pts() - first + offset
                        packet.dts(packet.dts() - first + offset)
                        packet.pts(pts)
                        recorder.recordPacket(packet)
                        next = pts + duration
                    }
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    } finally {
        runCatching { grabber.stop() }
        grabber.release()
    }
}

internal fun audioDescription(file: File): String = FFmpegFrameGrabber(file).use { grabber ->
    grabber.start()
    "codec=${grabber.audioCodecName} rate=${grabber.sampleRate} channels=${grabber.audioChannels} " +
        "durationMs=${grabber.lengthInTime / 1000} streams=${grabber.formatContext.nb_streams()}"
}
