package com.woka.unit

import com.woka.feature.editor.engine.video.rulerStepMs
import com.woka.feature.editor.engine.video.snapBody
import com.woka.feature.editor.engine.video.snapPoints
import com.woka.feature.editor.engine.video.snapTo
import com.woka.feature.editor.tools.trim.TrimClip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a Markup bar snaps to on the timeline (RD-301, the prototype's `snapTime`), and the ruler's step. */
class TimelineSnapTest {

    private val clips = listOf(TrimClip("a", 0L, 4_000L), TrimClip("b", 10_000L, 16_000L))

    @Test
    fun `the points are the start, the play head, every join and the end`() {
        assertEquals(listOf(0L, 2_500L, 4_000L, 10_000L), snapPoints(clips, playHeadMs = 2_500L))
    }

    @Test
    fun `a value within reach lands on the nearest point and one out of reach is left alone`() {
        val points = snapPoints(clips, playHeadMs = 2_500L)
        assertEquals(4_000L, snapTo(3_920L, points, reachMs = 100L))
        assertEquals(2_500L, snapTo(2_560L, points, reachMs = 100L), "the nearer of two points within reach")
        assertNull(snapTo(3_200L, points, reachMs = 100L))
    }

    @Test
    fun `a negative reach, Alt held, snaps nothing`() {
        assertNull(snapTo(4_000L, listOf(4_000L), reachMs = -1L))
    }

    @Test
    fun `a body snaps by its start first, else by its end`() {
        val points = listOf(0L, 4_000L, 10_000L)
        assertEquals(4_000L, snapBody(3_950L, holdMs = 2_000L, points, reachMs = 100L).atMs)
        val byEnd = snapBody(2_040L, holdMs = 2_000L, points, reachMs = 100L)
        assertEquals(2_000L, byEnd.atMs, "the end at 4 s is what snapped")
        assertEquals(4_000L, byEnd.snapped)
        assertNull(snapBody(6_000L, holdMs = 1_000L, points, reachMs = 100L).snapped)
    }

    @Test
    fun `the ruler's step is the smallest 1, 2 or 5 that leaves the spacing`() {
        assertEquals(1_000L, rulerStepMs(pxPerMs = 0.1f, minPx = 80f))
        assertEquals(2_000L, rulerStepMs(pxPerMs = 0.05f, minPx = 80f))
        assertEquals(100L, rulerStepMs(pxPerMs = 2f, minPx = 80f))
        assertEquals(500_000L, rulerStepMs(pxPerMs = 0.0002f, minPx = 80f))
    }
}
