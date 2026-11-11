package com.woka.probe

import com.woka.feature.viewer.video.engine.audio.atempoChain
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16P
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.ShortBuffer
import java.util.concurrent.TimeUnit

/** TEMPORARY (ME-244/245): every mix position is an integer frame at stereo 48 kHz. */
internal const val MIX_RATE = 48_000
internal const val MIX_CHANNELS = 2

internal fun audioCommand(root: File, label: String, vararg args: String) {
    val log = File(root, "$label.log")
    val process = ProcessBuilder(listOf("ffmpeg", "-hide_banner", "-nostdin", "-y") + args)
        .redirectErrorStream(true).redirectOutput(log).start()
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        error("$label exceeded 60 seconds")
    }
    check(process.exitValue() == 0) { "$label failed: $log" }
}

internal fun audioFixtures(root: File) {
    audioCommand(root, "film", "-f", "lavfi", "-i",
        "aevalsrc=0.2*sin(2*PI*(440*t+8*t*t))|0.2*sin(2*PI*(660*t+7*t*t)):s=48000:d=12",
        "-c:a", "pcm_s16le", File(root, "film.wav").path)
    audioCommand(root, "music", "-f", "lavfi", "-i", "sine=frequency=733:sample_rate=44100:duration=6",
        "-ac", "1", "-c:a", "pcm_s16le", File(root, "music.wav").path)
    audioCommand(root, "surround", "-f", "lavfi", "-i", "sine=frequency=997:sample_rate=96000:duration=6",
        "-ac", "6", "-c:a", "flac", File(root, "surround.flac").path)
}

internal fun audioGrabber(file: File): FFmpegFrameGrabber = FFmpegFrameGrabber(file).apply {
    videoStream = Int.MAX_VALUE
    audioChannels = MIX_CHANNELS
    sampleRate = MIX_RATE
    sampleFormat = AV_SAMPLE_FMT_S16
    start()
}

internal fun readAudio(file: File): ShortArray = audioGrabber(file).use { grabber ->
    val chunks = mutableListOf<ShortArray>()
    while (true) {
        val frame = grabber.grabSamples() ?: break
        val buffer = frame.samples[0] as ShortBuffer
        chunks += ShortArray(buffer.remaining()).also { buffer.get(it) }
    }
    joinAudio(chunks)
}

internal fun joinAudio(chunks: List<ShortArray>): ShortArray {
    val result = ShortArray(chunks.sumOf { it.size })
    var offset = 0
    for (chunk in chunks) {
        chunk.copyInto(result, offset)
        offset += chunk.size
    }
    return result
}

internal fun writeAudio(file: File, samples: ShortArray, codec: String) {
    val recorder = FFmpegFrameRecorder(file, MIX_CHANNELS)
    recorder.format = if (file.extension == "wav") "wav" else "ipod"
    recorder.audioCodecName = codec
    recorder.sampleRate = MIX_RATE
    recorder.audioBitrate = 192_000
    if (codec == "alac") recorder.sampleFormat = AV_SAMPLE_FMT_S16P
    try {
        recorder.start()
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(2048, samples.size - offset)
            recorder.recordSamples(MIX_RATE, MIX_CHANNELS, ShortBuffer.wrap(samples, offset, count))
            offset += count
        }
    } finally {
        runCatching { recorder.stop() }
        recorder.release()
    }
}

/** A tempo graph is drained, then its length reported before sample-exact padding/trimming. */
internal fun audioTempo(samples: ShortArray, speed: Float): ShortArray {
    val filter = FFmpegFrameFilter(atempoChain(speed), MIX_CHANNELS)
    filter.sampleRate = MIX_RATE
    filter.sampleFormat = AV_SAMPLE_FMT_S16
    val chunks = mutableListOf<ShortArray>()
    fun pull() {
        while (true) {
            val frame = filter.pullSamples() ?: break
            val buffer = frame.samples[0] as ShortBuffer
            chunks += ShortArray(buffer.remaining()).also { buffer.get(it) }
        }
    }
    try {
        filter.start()
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(1920, samples.size - offset)
            filter.pushSamples(0, MIX_CHANNELS, MIX_RATE, AV_SAMPLE_FMT_S16, ShortBuffer.wrap(samples, offset, count).slice())
            pull()
            offset += count
        }
        filter.push(null)
        pull()
    } finally {
        runCatching { filter.stop() }
        filter.release()
    }
    return joinAudio(chunks)
}

internal fun audioSlice(samples: ShortArray, fromMs: Int, untilMs: Int): ShortArray =
    samples.copyOfRange(fromMs * 96, minOf(untilMs * 96, samples.size))
