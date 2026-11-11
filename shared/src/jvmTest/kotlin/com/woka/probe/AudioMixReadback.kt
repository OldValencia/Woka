package com.woka.probe

import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameFilter
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.sqrt

/** TEMPORARY (ME-244): independent native amix reference, with normalisation explicitly disabled. */
internal fun nativeAudioMix(a: ShortArray, b: ShortArray): ShortArray {
    val graph = "[0:a][1:a]amix=inputs=2:weights='0.5 0.5':normalize=0:duration=shortest," +
        "aformat=sample_fmts=s16:channel_layouts=stereo[a]"
    val filter = FFmpegFrameFilter(graph, 2)
    filter.audioInputs = 2
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
        for (from in 0 until minOf(a.size, b.size) step 1920) {
            val count = minOf(1920, a.size - from, b.size - from)
            filter.pushSamples(0, 2, MIX_RATE, AV_SAMPLE_FMT_S16, ShortBuffer.wrap(a, from, count).slice())
            filter.pushSamples(1, 2, MIX_RATE, AV_SAMPLE_FMT_S16, ShortBuffer.wrap(b, from, count).slice())
            pull()
        }
        filter.push(0, null)
        filter.push(1, null)
        pull()
    } finally {
        runCatching { filter.stop() }
        filter.release()
    }
    return joinAudio(chunks)
}

internal fun audioDifference(expected: ShortArray, actual: ShortArray): String {
    val count = minOf(expected.size, actual.size)
    var sum = 0.0
    var largest = 0
    for (index in 0 until count) {
        val delta = abs(expected[index].toInt() - actual[index])
        sum += delta.toDouble() * delta
        largest = maxOf(largest, delta)
    }
    return "expectedSamples=${expected.size} actualSamples=${actual.size} maxError=$largest rmsLSB=${"%.3f".format(sqrt(sum / count.coerceAtLeast(1)))}"
}
