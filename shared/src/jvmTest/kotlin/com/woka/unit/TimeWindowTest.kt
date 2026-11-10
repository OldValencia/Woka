package com.woka.unit

import com.woka.feature.editor.core.TimeWindow
import kotlin.test.Test
import kotlin.test.assertEquals

/** The one curve markup's alpha and a sound entry's gain are read from (ME-263, `audio.md` §3.3a). */
class TimeWindowTest {

    /** The ticket's caption: at 0:05, for 3 s, a 0.5 s fade out. */
    private val caption = TimeWindow(atMs = 5_000L, holdMs = 3_000L, fadeOutMs = 500L)

    @Test
    fun `absent before it starts and from its end on`() {
        assertEquals(0f, caption.envelopeAt(4_999L))
        assertEquals(0f, caption.envelopeAt(8_000L))
        assertEquals(0f, caption.envelopeAt(60_000L))
    }

    @Test
    fun `with no fade in it is full from its first millisecond`() {
        assertEquals(1f, caption.envelopeAt(5_000L))
        assertEquals(1f, caption.envelopeAt(7_500L))
    }

    @Test
    fun `the fade out falls inside the hold`() {
        assertEquals(0.5f, caption.envelopeAt(7_750L))
        assertEquals(0.002f, caption.envelopeAt(7_999L), 0.0001f)
    }

    @Test
    fun `the fade in rises from zero`() {
        val window = TimeWindow(atMs = 1_000L, holdMs = 2_000L, fadeInMs = 400L)
        assertEquals(0f, window.envelopeAt(1_000L))
        assertEquals(0.5f, window.envelopeAt(1_200L))
        assertEquals(1f, window.envelopeAt(1_400L))
    }

    @Test
    fun `fades longer than the hold meet below full`() {
        val window = TimeWindow(atMs = 0L, holdMs = 1_000L, fadeInMs = 1_000L, fadeOutMs = 1_000L)
        assertEquals(0.5f, window.envelopeAt(500L))
        assertEquals(0.25f, window.envelopeAt(250L))
        assertEquals(0.25f, window.envelopeAt(750L))
    }

    @Test
    fun `an empty window is never present`() {
        assertEquals(0f, TimeWindow(atMs = 2_000L, holdMs = 0L).envelopeAt(2_000L))
        assertEquals(0f, TimeWindow(atMs = 2_000L, holdMs = -5L).envelopeAt(2_000L))
    }
}
