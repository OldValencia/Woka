package com.woka.unit

import com.woka.feature.editor.engine.video.StripDrag
import com.woka.feature.editor.engine.video.StripGeometry
import com.woka.feature.editor.engine.video.StripHandle
import com.woka.feature.editor.engine.video.trimmedEnds
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.trimEnd
import com.woka.feature.editor.tools.trim.wholeSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lane's layout while an outer end is dragged (reported by the author 2026-11-02; only during
 * that drag since RD-301): trimming the end moved the handle back to 100 % on release, because
 * the kept part re-flowed to the full width. With the trimmed stretch laid out, the handle stays
 * where it was let go.
 */
class StripGeometryTest {

    private val width = 1_000f

    /** Laid out under [drag] — by default the last clip's end, an outer end, held. */
    private fun laid(
        clips: List<TrimClip>,
        drag: StripDrag? = StripDrag.Edge(StripHandle(clips.lastIndex, leading = false), clips)
    ): StripGeometry {
        val (lead, tail) = trimmedEnds(clips, 26_000L, drag)
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
        val geometry = laid(trimmed, drag = null)
        assertEquals(width, geometry.spans.single().right, 0.5f)
        assertEquals(0f, geometry.tail.width)
    }

    @Test
    fun `an inner edge held lays out nothing trimmed`() {
        val clips = listOf(TrimClip("a", 2_000L, 10_000L), TrimClip("b", 10_000L, 20_000L))
        val geometry = laid(clips, StripDrag.Edge(StripHandle(0, leading = false), clips))
        assertEquals(0f, geometry.lead.width)
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
