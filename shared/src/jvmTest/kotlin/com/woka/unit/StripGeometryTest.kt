package com.woka.unit

import com.woka.feature.editor.engine.video.StripGeometry
import com.woka.feature.editor.engine.video.trimmedEnds
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.trimEnd
import com.woka.feature.editor.tools.trim.wholeSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The strip's layout with Trim open (reported by the author 2026-11-02): trimming the end
 * moved the handle back to 100 % on release, because the kept part re-flowed to the full
 * width. With the trimmed stretch laid out, the handle stays where it was let go.
 */
class StripGeometryTest {

    private val width = 1_000f

    private fun laid(clips: List<TrimClip>, editing: Boolean = true): StripGeometry {
        val (lead, tail) = trimmedEnds(clips, 26_000L, editing)
        return StripGeometry(clips, width, gapPx = 3f, leadMs = lead, tailMs = tail)
    }

    @Test
    fun `a trimmed end keeps its place and its handle stays where it was let go`() {
        val trimmed = wholeSource(26_000L).trimEnd("source", 13_000L, 26_000L)
        val geometry = laid(trimmed)
        assertEquals(500f, geometry.spans.single().right, 0.5f)
        assertEquals(500f, geometry.tail.width, 0.5f)
        assertEquals(0f, geometry.lead.width)
    }

    @Test
    fun `at rest the strip shows only what is kept`() {
        val trimmed = wholeSource(26_000L).trimEnd("source", 13_000L, 26_000L)
        val geometry = laid(trimmed, editing = false)
        assertEquals(width, geometry.spans.single().right, 0.5f)
        assertEquals(0f, geometry.tail.width)
    }

    @Test
    fun `the play head and the pointer agree across a trimmed start`() {
        val clips = listOf(TrimClip("a", 6_500L, 26_000L))
        val geometry = laid(clips)
        assertEquals(250f, geometry.spans.single().left, 0.5f)
        assertEquals(250f, geometry.xOf(0L), 0.5f)
        assertEquals(0L, geometry.timelineAt(100f), "a press on the trimmed stretch is the start")
        // A third of the kept 19.5 s lies between x 250 and x 500 of 1000.
        assertTrue(abs(geometry.timelineAt(500f) - 6_500L) <= 30L, "x 500 is ${geometry.timelineAt(500f)}")
    }
}
