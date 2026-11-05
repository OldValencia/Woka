package com.woka.unit

import com.woka.core.ui.image.EditedClock
import com.woka.core.ui.image.SourceRange
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.join
import com.woka.feature.editor.tools.trim.split
import com.woka.feature.editor.tools.trim.timelineOf
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.editor.tools.trim.wholeSource
import com.woka.feature.editor.tools.trim.snapClipSpeed
import com.woka.feature.editor.tools.trim.withSpeed
import com.woka.feature.viewer.video.engine.audio.atempoChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A clip's speed (ME-216): its length on the timeline, the two clocks through it in the
 * editor and in the viewer, the edits that must carry it, and the sound filter for it.
 */
class ClipSpeedTest {

    private val clips = listOf(TrimClip("a", 0L, 8_000L, speed = 2f), TrimClip("b", 8_000L, 10_000L, speed = 0.5f))

    @Test
    fun `a clip lasts its source span divided by its speed`() {
        assertEquals(listOf(4_000L, 4_000L), clips.map { it.lengthMs })
        assertEquals(8_000L, clips.totalMs())
    }

    @Test
    fun `timeline and source map through the speed, both ways`() {
        assertEquals(4_000L, clips.atTimeline(2_000L)!!.sourceMs, "2 s into a 2x clip is 4 s of source")
        assertEquals(9_000L, clips.atTimeline(6_000L)!!.sourceMs, "2 s into a half-speed clip is 1 s of source")
        assertEquals(2_000L, clips.timelineOf(0, 4_000L))
        assertEquals(6_000L, clips.timelineOf(1, 9_000L))
    }

    @Test
    fun `the viewer's clock agrees with the editor's`() {
        val clock = EditedClock(clips.map { SourceRange(it.fromMs, it.toMs, it.key, it.speed) })
        assertEquals(8_000L, clock.totalMs)
        assertEquals(1 to 9_000L, clock.toSource(6_000L))
        clock.index = 1
        assertEquals(6_000L, clock.toTimeline(9_000L))
    }

    @Test
    fun `a split keeps the speed, and a join will not mix two`() {
        val fast = wholeSource(10_000L).withSpeed("source", 2f)
        val cut = fast.split(2_000L, "b")
        assertEquals(listOf(2f, 2f), cut.map { it.speed })
        val mixed = cut.withSpeed("b", 1f)
        assertSame(mixed, mixed.join("source"), "two speeds were joined into one clip")
    }

    @Test
    fun `quarter speed is two halvings of atempo`() {
        assertEquals("atempo=0.5,atempo=0.5", atempoChain(0.25f))
        assertEquals("atempo=4.0", atempoChain(4f))
    }

    /** 2026-11-04: five fixed speeds were too few — the slider steps by 0.05 and sticks. */
    @Test
    fun `the slider sticks to the named speeds and steps by a twentieth between`() {
        assertEquals(1.25f, snapClipSpeed(1.27f))
        assertEquals(1f, snapClipSpeed(1.03f))
        assertEquals(1.65f, snapClipSpeed(1.66f))
        assertEquals(4f, snapClipSpeed(9f))
    }
}

