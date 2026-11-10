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
                    loudest = maxOf(loudest, abs(samples.get(i).toInt()))
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
            filledMs.longValue = peaks.size * PEAK_MS
        }
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}

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
    val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
    val peaks = remember(key) {
        measured[key] ?: AudioPeaks(file, durationMs).also { fresh ->
            if (measured.size >= KEPT) measured.keys.firstOrNull()?.let { measured.remove(it)?.stop() }
            measured[key] = fresh
            fresh.start()
        }
    }
    return peaks
}
