package com.woka.unit

import com.woka.feature.editor.engine.video.ClipSpan
import com.woka.feature.editor.engine.video.StripGeometry
import com.woka.feature.editor.engine.video.StripLayout
import com.woka.feature.editor.engine.video.StripZoom
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.split
import com.woka.feature.editor.tools.trim.wholeSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The strip's zoom (ME-218): an 81-minute film, a 1000 px strip, and the two-second freeze
 * the author could not catch (2026-11-04).
 */
class StripZoomTest {

    private val film = wholeSource(81 * 60_000L)

    private fun layout(clips: List<TrimClip>): StripLayout = { scale, scroll -> StripGeometry(clips, 1000f, 3f, scale = scale, scrollPx = scroll) }

    @Test
    fun `zoomed, the film is laid across several widths and still maps both ways`() {
        val fit = StripGeometry(film, 1000f, 3f).fitPxPerMs
        val geometry = StripGeometry(film, 1000f, 3f, scale = fit * 4f, scrollPx = 1500f)
        assertTrue(abs(geometry.contentPx - 4000f) < 1f, "4x is four widths, not ${geometry.contentPx}")
        val ms = geometry.timelineAt(500f)
        assertTrue(abs(geometry.xOf(ms) - 500f) < 1f, "x → time → x drifted: ${geometry.xOf(ms)}")
    }

    @Test
    fun `the wheel zooms around the pointer and stops at five seconds a strip`() {
        val zoom = StripZoom()
        val at = layout(film)(null, 0f).timelineAt(700f)
        zoom.zoomAround(700f, 8f, layout(film))
        val after = layout(film)(zoom.scale, zoom.scrollPx)
        assertTrue(abs(after.xOf(at) - 700f) < 2f, "the moment under the pointer moved to ${after.xOf(at)}")
        repeat(40) { zoom.zoomAround(700f, 8f, layout(film)) }
        assertEquals(1000f / 5_000f, zoom.scale, "five seconds a strip is 0.2 px per ms")
        zoom.fit()
        assertEquals(null to 0f, zoom.scale to zoom.scrollPx)
    }

    @Test
    fun `a two-second freeze becomes wide enough to take hold of`() {
        val clips = film.split(40 * 60_000L, "b").let { it.take(1) + TrimClip("f", 2_400_000L, 2_400_000L, holdMs = 2_000L) + it.drop(1) }
        val zoom = StripZoom()
        zoom.zoomAround(500f, 1000f, layout(clips))
        val width = layout(clips)(zoom.scale, zoom.scrollPx).spans[1].width
        assertTrue(width > 300f, "zoomed all the way the freeze is $width px wide")
    }

    /** 2026-11-04: zooming in was fine; moving sideways was not — the overview moves it. */
    @Test
    fun `the overview centres the window on a press and moves it with a drag`() {
        val zoom = StripZoom().apply { layoutNow = layout(film) }
        zoom.zoomAround(0f, 4f, layout(film))
        zoom.centreOn(500f)
        assertTrue(abs(zoom.scrollPx - 1500f) < 2f, "the window is not centred on the middle: ${zoom.scrollPx}")
        zoom.panOverview(100f)
        assertTrue(abs(zoom.scrollPx - 1900f) < 2f, "100 px of overview is 400 px of a 4x strip: ${zoom.scrollPx}")
        zoom.panOverview(10_000f)
        assertTrue(abs(zoom.scrollPx - 3000f) < 2f, "the window ran off the end of the film: ${zoom.scrollPx}")
    }

    @Test
    fun `the wheel away from the strip zooms around the play head`() {
        val head = 60 * 60_000L
        val zoom = StripZoom().apply {
            layoutNow = layout(film)
            playHeadMsNow = { head }
        }
        val before = layout(film)(null, 0f).xOf(head)
        zoom.wheelAtPlayHead(-3f)
        assertTrue(abs(layout(film)(zoom.scale, zoom.scrollPx).xOf(head) - before) < 2f, "the play head moved")
    }

    @Test
    fun `only the part of a span on screen is composed`() {
        assertEquals(ClipSpan(0f, 300f), ClipSpan(-500f, 800f).visibleIn(1000f))
        assertEquals(null, ClipSpan(1200f, 50f).visibleIn(1000f))
    }
}

