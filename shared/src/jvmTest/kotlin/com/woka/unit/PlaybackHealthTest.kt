package com.woka.unit

import com.woka.feature.viewer.video.engine.PlaybackHealth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The policy only — no decoder, no file, no waiting. The case that matters most is
 * the first one: a machine that copes must never be accused, and that is the whole
 * false-positive risk of the feature.
 */
class PlaybackHealthTest {

    private var now = 0L
    private val health = PlaybackHealth(nowMs = { now })

    /** Feeds [fps] frames a second for [seconds], evenly spaced. */
    private fun play(fps: Int, seconds: Int, declared: Double = 24.0) {
        val step = 1000L / fps
        repeat(fps * seconds) {
            health.onFramePresented(declared)
            now += step
        }
    }

    @Test
    fun `a machine keeping up is never reported`() {
        play(fps = 24, seconds = 60)
        assertFalse(health.isStruggling, "a healthy machine was reported as struggling")
    }

    @Test
    fun `slightly under rate is not reported`() {
        // 19 of 24 is 79% — above the 70% line, and normal jitter.
        play(fps = 19, seconds = 60)
        assertFalse(health.isStruggling)
    }

    @Test
    fun `sustained low frame rate is reported with its numbers`() {
        play(fps = 10, seconds = 8)
        assertTrue(health.isStruggling, "a struggling machine was not reported")
        assertEquals(24, health.expectedFps)
        assertTrue(health.displayedFps in 9..11, "quoted ${health.displayedFps} fps")
    }

    @Test
    fun `a short dip is forgiven`() {
        play(fps = 10, seconds = 2)
        play(fps = 24, seconds = 2)
        play(fps = 10, seconds = 2)
        assertFalse(health.isStruggling, "a passing stutter was reported")
    }

    @Test
    fun `suspending clears the run in progress`() {
        play(fps = 10, seconds = 4)
        health.suspendMeasuring() // a seek, a pause, a track swap
        play(fps = 10, seconds = 4)
        assertFalse(health.isStruggling, "lateness carried across a suspend")
    }

    @Test
    fun `an implausible declared rate is not measured against`() {
        repeat(500) {
            health.onFramePresented(0.0)
            now += 100
        }
        repeat(500) {
            health.onFramePresented(null)
            now += 100
        }
        assertFalse(health.isStruggling)
    }

    @Test
    fun `it reports at most once`() {
        play(fps = 10, seconds = 8)
        assertTrue(health.isStruggling)
        val quoted = health.displayedFps
        play(fps = 2, seconds = 30)
        assertEquals(quoted, health.displayedFps, "the notice was recomputed after reporting")
    }
}
