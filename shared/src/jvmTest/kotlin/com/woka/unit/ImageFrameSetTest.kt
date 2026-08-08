package com.woka.unit

import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.ImageFrameSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun frame(index: Int, kind: FrameKind) = ImageFrame(index, kind)

/**
 * What is inside an image file, as a value.
 *
 * The type the whole feature is built on: "a still is not a bitmap, it is a
 * frame set of which the common case has exactly one frame". These are the
 * rules every decoder's output is read through.
 */
class ImageFrameSetTest {

    @Test
    fun `the common case is one frame and knows it`() {
        assertTrue(ImageFrameSet.Single.isSingle)
        assertEquals(1, ImageFrameSet.Single.frames.size)
        assertEquals(FrameKind.Primary, ImageFrameSet.Single.primary?.kind)
    }

    /** An empty set is single too: there is no strip to draw for it either. */
    @Test
    fun `an empty set counts as single rather than as a set`() {
        assertTrue(ImageFrameSet(emptyList()).isSingle)
        assertNull(ImageFrameSet(emptyList()).primary)
    }

    @Test
    fun `more than one frame is a set`() {
        val burst = ImageFrameSet((0 until 5).map { frame(it, FrameKind.Sequence) })
        assertFalse(burst.isSingle)
        assertEquals(5, burst.frames.size)
    }

    /**
     * Depth maps and auxiliary images are **recorded but not offered**. A depth
     * map shown as a frame is a grey smear that reads as a decode failure, and
     * dropping it at the source would mean re-deriving it the day the editor
     * wants it.
     */
    @Test
    fun `depth and auxiliary frames are kept but never offered`() {
        val set = ImageFrameSet(
            listOf(
                frame(0, FrameKind.Primary),
                frame(1, FrameKind.Depth),
                frame(2, FrameKind.Sequence),
                frame(3, FrameKind.Auxiliary),
                frame(4, FrameKind.Motion)
            )
        )
        assertEquals(5, set.frames.size, "the set stopped recording what it cannot show")
        assertEquals(
            listOf(FrameKind.Primary, FrameKind.Sequence, FrameKind.Motion),
            set.viewable.map { it.kind }
        )
    }

    @Test
    fun `viewable keeps file order rather than sorting by kind`() {
        val set = ImageFrameSet(
            listOf(frame(0, FrameKind.Motion), frame(1, FrameKind.Primary))
        )
        assertEquals(listOf(0, 1), set.viewable.map { it.index })
    }

    @Test
    fun `the primary frame is the one the index points at`() {
        val set = ImageFrameSet(
            frames = (0 until 3).map { frame(it, FrameKind.Sequence) },
            primaryIndex = 2
        )
        assertEquals(2, set.primary?.index)
    }

    /**
     * A decoder that disagrees with itself about how many images it found must
     * not crash the viewer: showing the wrong frame is a nuisance, throwing on a
     * malformed HEIC is a bug report.
     */
    @Test
    fun `an out-of-range primary index falls back to the first frame`() {
        val set = ImageFrameSet(
            frames = listOf(frame(7, FrameKind.Primary), frame(8, FrameKind.Sequence)),
            primaryIndex = 99
        )
        assertEquals(7, set.primary?.index)
    }

    @Test
    fun `a negative primary index falls back too`() {
        val set = ImageFrameSet(listOf(frame(0, FrameKind.Primary)), primaryIndex = -1)
        assertEquals(0, set.primary?.index)
    }

    /** Only animation carries timings; a burst's frames are not a sequence to play. */
    @Test
    fun `duration is optional and absent by default`() {
        assertNull(frame(0, FrameKind.Sequence).durationMs)
        assertEquals(120, ImageFrame(0, FrameKind.Sequence, durationMs = 120).durationMs)
    }
}
