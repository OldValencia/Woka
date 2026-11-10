package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import com.woka.feature.editor.engine.video.render.containerStartUs
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import java.io.File
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** One peak per this many milliseconds of sound: 360 KB for a two-hour film. */
internal const val PEAK_MS = 20L

/** How often, in peaks, the strip is told more of the film is ready — every two seconds of sound. */
private const val PUBLISH_EVERY = 100

/**
 * A film's sound as one peak per [PEAK_MS], `0..255` of full scale (ME-241, `audio.md` §3.6) —
 * the waveform's whole input, and the measurement ME-246's loudness will read.
 *
 * **One audio-only pass, once.** No video stream is opened; the pass runs on its own low-priority
 * thread and fills [peaks] from the start, telling the strip how far it has got through
 * [filledMs], so a long film's wave grows rather than waits. Kept per file — path, length and
 * mtime, the poster's key — so reopening the editor on the same film decodes nothing.
 */
internal class AudioPeaks(private val file: File, durationMs: Long) {

    val peaks = ByteArray((durationMs / PEAK_MS + 1).toInt())

    /** How much of the film from its start [peaks] holds, read by the strip's draw. */
    val filledMs = mutableLongStateOf(0L)

    @Volatile
    private var stopped = false

    /** The sum of every sample's square and how many there were — the film's RMS (ME-246). */
    private var squares = 0.0
    private var counted = 0L

    @Volatile
    private var measuredGain: Float? = null

    /**
     * The gain that brings the film's RMS to −18 dBFS (ME-246) — capped at +12 dB, and at what
     * keeps the loudest peak under full scale. Null until the whole film has been measured.
     */
    fun evenGain(): Float? = measuredGain

    private val pass = Thread(::decode, "woka-waveform").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
    }

    fun start() = pass.start()

    fun stop() {
        stopped = true
    }

    /** The loudest peak over `[fromMs, toMs)`, `0f..1f`; zero where none is known yet. */
    fun peakIn(fromMs: Long, toMs: Long): Float {
        val first = (fromMs / PEAK_MS).toInt().coerceIn(0, peaks.lastIndex)
        val last = ((toMs - 1) / PEAK_MS).toInt().coerceIn(first, peaks.lastIndex)
        var loudest = 0
        for (i in first..last) loudest = maxOf(loudest, peaks[i].toInt() and 0xFF)
        return loudest / 255f
    }

    private fun decode() {
        val grabber = FreshFrameGrabber(file).apply {
            videoStream = Int.MAX_VALUE
            sampleFormat = AV_SAMPLE_FMT_S16
        }
        runCatching {
            grabber.start()
            if (grabber.audioChannels <= 0) return@runCatching
            val start = containerStartUs(grabber)
            var sinceTold = 0
            while (!stopped) {
                val frame = grabber.grabSamples() ?: break
                val samples = frame.samples?.firstOrNull() as? ShortBuffer ?: continue
                val at = ((frame.timestamp - start) / 1000L / PEAK_MS).toInt()
                if (at !in peaks.indices) continue
                val perPeak = (grabber.sampleRate * PEAK_MS / 1000L * grabber.audioChannels).toInt().coerceAtLeast(1)
                var index = at
                var loudest = 0
                for (i in 0 until samples.limit()) {
                    val sample = samples.get(i).toInt()
                    loudest = maxOf(loudest, abs(sample))
                    squares += sample.toDouble() * sample
                    counted++
                    if ((i + 1) % perPeak == 0 || i == samples.limit() - 1) {
                        if (index in peaks.indices) peaks[index] = maxOf(peaks[index].toInt() and 0xFF, loudest * 255 / 32768).toByte()
                        index++
                        loudest = 0
                    }
                }
                if (++sinceTold >= PUBLISH_EVERY) {
                    sinceTold = 0
                    filledMs.longValue = index * PEAK_MS
                }
            }
            if (!stopped) measuredGain = gainFor(squares, counted, peaks)
            filledMs.longValue = peaks.size * PEAK_MS
        }
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}

/** RMS to −18 dBFS, at most +12 dB and never past the loudest peak; 1 for silence. */
private fun gainFor(squares: Double, counted: Long, peaks: ByteArray): Float {
    if (counted == 0L || squares <= 0.0) return 1f
    val rms = sqrt(squares / counted) / 32768.0
    val target = 10.0.pow(-18.0 / 20.0)
    val loudest = (peaks.maxOf { it.toInt() and 0xFF } / 255.0).coerceAtLeast(1e-6)
    return minOf(target / rms, 4.0, 1.0 / loudest).toFloat()
}

private fun keyOf(file: File) = "${file.absolutePath}|${file.length()}|${file.lastModified()}"

/** [file]'s even-loudness gain, once the strip has measured it whole (ME-246); else null. */
internal fun evenGainOf(file: File): Float? = measured[keyOf(file)]?.evenGain()

/** The films whose sound has been measured, by path, length and mtime; a handful at most. */
private val measured = ConcurrentHashMap<String, AudioPeaks>()

private const val KEPT = 4

/**
 * [file]'s peaks — measured once, then kept for the next sitting; null for no film yet. The pass
 * runs to the film's end even if the editor closes first, so the next opening finds it whole.
 */
@Composable
internal fun rememberAudioPeaks(file: File?, durationMs: Long): AudioPeaks? {
    if (file == null || durationMs <= 0L) return null
    val key = keyOf(file)
    val peaks = remember(key) {
        measured[key] ?: AudioPeaks(file, durationMs).also { fresh ->
            if (measured.size >= KEPT) measured.keys.firstOrNull()?.let { measured.remove(it)?.stop() }
            measured[key] = fresh
            fresh.start()
        }
    }
    return peaks
}
