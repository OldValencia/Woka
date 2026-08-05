package com.woka.unit

import com.woka.feature.viewer.video.engine.buildTimestampCandidates
import com.woka.feature.viewer.video.engine.isMonochrome
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Timestamps are microseconds; ten seconds is the shortest "long" video. */
private const val TEN_SECONDS = 10_000_000L

private fun image(width: Int, height: Int, rgbAt: (Int, Int) -> Int): BufferedImage {
    val bitmap = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            bitmap.setRGB(x, y, rgbAt(x, y))
        }
    }
    return bitmap
}

private fun grey(level: Int) = (level shl 16) or (level shl 8) or level

class VideoThumbnailHeuristicsTest {

    @Test
    fun `a long video is sampled inside the 5 to 25 percent band`() {
        repeat(50) {
            val candidates = buildTimestampCandidates(TEN_SECONDS)
            assertEquals(3, candidates.size)
            candidates.forEach { at ->
                assertTrue(at >= TEN_SECONDS * 5 / 100, "candidate $at is in the opening")
                assertTrue(at < TEN_SECONDS * 25 / 100, "candidate $at is past the band")
            }
        }
    }

    @Test
    fun `the three candidates walk forward through the band`() {
        repeat(50) {
            val candidates = buildTimestampCandidates(TEN_SECONDS)
            assertEquals(candidates, candidates.sorted(), "the band was sampled out of order")
        }
    }

    @Test
    fun `regenerating a thumbnail can land somewhere else`() {
        val runs = List(40) { buildTimestampCandidates(TEN_SECONDS) }
        assertTrue(runs.toSet().size > 1, "the candidates are not randomised at all")
    }

    @Test
    fun `a very short video is sampled at its midpoint`() {
        assertEquals(listOf(250_000L), buildTimestampCandidates(500_000L))
        assertEquals(listOf(500_000L), buildTimestampCandidates(1_000_000L))
        assertEquals(listOf(0L), buildTimestampCandidates(1L))
    }

    @Test
    fun `an unknown duration falls back to fixed probes`() {
        val fallback = listOf(2_000_000L, 5_000_000L, 10_000_000L, 20_000_000L)
        assertEquals(fallback, buildTimestampCandidates(0L))
        assertEquals(fallback, buildTimestampCandidates(-1L))
    }

    @Test
    fun `a flat colour is rejected`() {
        assertTrue(isMonochrome(image(200, 120) { _, _ -> grey(0) }))
        assertTrue(isMonochrome(image(200, 120) { _, _ -> grey(32) }))
        assertTrue(isMonochrome(image(200, 120) { _, _ -> 0xFFFFFF }))
    }

    // The grid samples every 6th row of a 120-row frame, so the banding below is
    // keyed on `y / 6` — a per-row pattern would fall entirely between samples.
    @Test
    fun `a near-flat fade is still rejected`() {
        assertTrue(isMonochrome(image(200, 120) { _, y -> grey(100 + (y / 6) % 2 * 15) }))
    }

    @Test
    fun `variation of 18 counts as a picture`() {
        assertFalse(isMonochrome(image(200, 120) { _, y -> grey(100 + (y / 6) % 2 * 18) }))
    }

    @Test
    fun `a real frame is kept`() {
        val frame = image(200, 120) { _, y -> if (y < 60) grey(0) else grey(255) }
        assertFalse(isMonochrome(frame))
    }

    @Test
    fun `one varying channel is enough to keep the frame`() {
        val blueOnly = image(200, 120) { _, y -> 0x404000 or ((y / 6) % 2 * 200) }
        assertFalse(isMonochrome(blueOnly))
    }

    @Test
    fun `a frame smaller than the sampling grid is still judged`() {
        assertTrue(isMonochrome(image(1, 1) { _, _ -> grey(70) }))
        assertFalse(isMonochrome(image(4, 4) { x, _ -> if (x < 2) grey(0) else grey(255) }))
    }
}
