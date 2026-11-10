package com.woka.integration

import com.woka.feature.editor.engine.video.AudioPeaks
import com.woka.feature.editor.engine.video.PEAK_MS
import com.woka.probe.generateClip
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A film's sound measured for the waveform (ME-241): the fixture's 440 Hz tone at 6000 of 32768
 * reads as a peak of about 0.18 everywhere, and the pass reports the whole film done.
 */
class AudioPeaksTest {

    private val dir: File = Files.createTempDirectory("woka-peaks").toFile()

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    @Test
    fun `a tone measures at its level from start to end`() {
        val source = File(dir, "clip.mp4").also { generateClip(it, "libopenh264", seconds = 4, gop = 15) }
        val peaks = AudioPeaks(source, durationMs = 4_000L)
        peaks.start()
        val deadline = System.currentTimeMillis() + 20_000L
        while (peaks.filledMs.longValue < peaks.peaks.size * PEAK_MS && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L)
        }
        assertEquals(peaks.peaks.size * PEAK_MS, peaks.filledMs.longValue, "the pass did not finish")
        listOf(200L, 1_500L, 3_500L).forEach { at ->
            val level = peaks.peakIn(at, at + 200L)
            assertTrue(level in 0.12f..0.26f, "the tone reads $level at $at ms")
        }
        // ME-246: a 6000-of-32768 sine is about −18 dBFS RMS, so it needs about no gain.
        val gain = peaks.evenGain() ?: error("no even gain after a finished pass")
        assertTrue(gain in 0.9f..1.1f, "the tone's even gain is $gain")
    }
}
