package com.woka.unit

import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.handles
import com.woka.feature.editor.tools.markup.hitTest
import com.woka.feature.editor.tools.markup.movedBy
import com.woka.feature.editor.tools.markup.withHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The arithmetic ME-114's gestures are made of (ME-113).
 *
 * Every one of these is what a pointer event will ask, tested without a pointer: if the
 * overlay has to compute any of it itself, this file is missing a function.
 */
class MarkupGeometryTest {

    private val arrow =
        MarkupShape.Arrow("a", 0L, 4f, MarkupPoint(0f, 0f), MarkupPoint(100f, 0f))

    private val box =
        MarkupShape.Rectangle("b", 0L, 4f, MarkupPoint(0f, 0f), MarkupPoint(100f, 60f))

    private val oval =
        MarkupShape.Ellipse("c", 0L, 4f, MarkupPoint(0f, 0f), MarkupPoint(100f, 60f))

    private val scribble = MarkupShape.Freehand(
        id = "d",
        color = 0L,
        width = 4f,
        points = listOf(MarkupPoint(0f, 0f), MarkupPoint(50f, 0f), MarkupPoint(50f, 50f))
    )

    @Test
    fun `a point on a stroke hits and one beyond the tolerance does not`() {
        assertTrue(arrow.hitTest(50f, 3f, tolerance = 5f), "the shaft was not hit")
        assertFalse(arrow.hitTest(50f, 20f, tolerance = 5f), "a miss was reported as a hit")

        assertTrue(scribble.hitTest(50f, 25f, tolerance = 5f), "the second segment was not hit")
        assertFalse(scribble.hitTest(20f, 25f, tolerance = 5f), "the corner it never entered hit")
    }

    /** A box and an ellipse are stroked and hollow, so their middle belongs to what is under them. */
    @Test
    fun `a box and an ellipse are hit on the outline and not in the middle`() {
        assertTrue(box.hitTest(0f, 30f, tolerance = 5f), "the left edge was not hit")
        assertFalse(box.hitTest(50f, 30f, tolerance = 5f), "the hollow middle was hit")

        assertTrue(oval.hitTest(100f, 30f, tolerance = 5f), "the right of the oval was not hit")
        assertFalse(oval.hitTest(50f, 30f, tolerance = 5f), "the hollow middle was hit")
        // The corner of its box, which the oval itself never reaches.
        assertFalse(oval.hitTest(0f, 0f, tolerance = 5f), "the bounding box was hit, not the oval")
    }

    @Test
    fun `an arrow reports two handles, a box four, and a freehand stroke none`() {
        assertEquals(listOf(MarkupPoint(0f, 0f), MarkupPoint(100f, 0f)), arrow.handles())
        assertEquals(
            listOf(
                MarkupPoint(0f, 0f),
                MarkupPoint(100f, 0f),
                MarkupPoint(100f, 60f),
                MarkupPoint(0f, 60f)
            ),
            box.handles()
        )
        assertEquals(4, oval.handles().size)
        assertEquals(emptyList(), scribble.handles())
    }

    @Test
    fun `movedBy translates every point and changes nothing else`() {
        val moved = assertIs<MarkupShape.Arrow>(arrow.movedBy(10f, -5f))
        assertEquals(MarkupPoint(10f, -5f), moved.from)
        assertEquals(MarkupPoint(110f, -5f), moved.to)
        assertEquals(arrow.copy(from = moved.from, to = moved.to), moved, "the pen changed")

        assertEquals(
            listOf(MarkupPoint(1f, 1f), MarkupPoint(51f, 1f), MarkupPoint(51f, 51f)),
            assertIs<MarkupShape.Freehand>(scribble.movedBy(1f, 1f)).points
        )
    }

    @Test
    fun `withHandle moves one corner and leaves the opposite one fixed`() {
        val resized = assertIs<MarkupShape.Rectangle>(box.withHandle(1, 140f, -20f))

        // Handle 1 is the top-right: it sets the top and the right, so the bottom-left
        // corner — handle 3 — must not have moved at all.
        assertEquals(MarkupPoint(140f, -20f), resized.handles()[1])
        assertEquals(box.handles()[3], resized.handles()[3], "the opposite corner moved")

        assertEquals(MarkupPoint(7f, 8f), assertIs<MarkupShape.Arrow>(arrow.withHandle(1, 7f, 8f)).to)
    }

    /** A pointer event is the caller, so an index this shape has no handle for cannot throw. */
    @Test
    fun `a handle this shape does not have leaves it untouched`() {
        assertEquals(arrow, arrow.withHandle(2, 5f, 5f))
        assertEquals(box, box.withHandle(9, 5f, 5f))
        assertEquals(scribble, scribble.withHandle(0, 5f, 5f))
    }
}
