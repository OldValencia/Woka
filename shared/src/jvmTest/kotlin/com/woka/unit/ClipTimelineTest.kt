package com.woka.unit

import com.woka.feature.editor.tools.trim.ClipPosition
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.clipOf
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.timelineOf
import com.woka.feature.editor.tools.trim.totalMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Timeline time against source time (ME-231) — the file every off-by-one in the video
 * editor would live in, tested without a player.
 *
 * The list is `timeline.md` §3.2's own example, the middle of a 40 s clip moved to the
 * end, so every join here is a place the two clocks disagree.
 */
class ClipTimelineTest {

    private val clips = listOf(
        TrimClip("a", 0L, 8_400L),
        TrimClip("c", 22_100L, 29_600L),
        TrimClip("b", 8_400L, 22_100L)
    )

    @Test
    fun `the result is as long as what it keeps`() {
        assertEquals(8_400L + 7_500L + 13_700L, clips.totalMs())
        assertEquals(15_900L, clips.startOf(2))
    }

    @Test
    fun `a timeline position maps into the clip that holds it`() {
        assertEquals(ClipPosition(0, 1_000L), clips.atTimeline(1_000L))
        assertEquals(ClipPosition(1, 22_100L), clips.atTimeline(8_400L), "a join belongs to the clip that starts on it")
        assertEquals(ClipPosition(2, 8_500L), clips.atTimeline(16_000L))
    }

    @Test
    fun `positions outside the timeline clamp to its ends`() {
        assertEquals(ClipPosition(0, 0L), clips.atTimeline(-500L))
        assertEquals(ClipPosition(2, 22_100L), clips.atTimeline(99_000L))
        assertNull(emptyList<TrimClip>().atTimeline(0L))
    }

    @Test
    fun `timelineOf is the inverse of atTimeline`() {
        listOf(0L, 4_200L, 8_400L, 12_000L, 15_899L, 15_900L, 29_599L).forEach { at ->
            val position = clips.atTimeline(at)!!
            assertEquals(at, clips.timelineOf(position.index, position.sourceMs), "round trip of $at")
        }
    }

    @Test
    fun `a duplicated range keeps playing the copy it was in`() {
        val doubled = listOf(TrimClip("x", 0L, 1_000L), TrimClip("y", 0L, 1_000L))
        assertEquals(1, doubled.clipOf(500L, hint = 1))
        assertEquals(0, doubled.clipOf(500L, hint = 0))
        assertEquals(0, doubled.clipOf(500L, hint = 7), "an index that does not exist falls back to the first holder")
        assertNull(doubled.clipOf(1_000L, hint = 0), "the end is exclusive")
    }
}
