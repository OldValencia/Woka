package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.tools.crop.CropAspect
import com.woka.feature.editor.tools.crop.CropHandle
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.clampedTo
import com.woka.feature.editor.tools.crop.cropFitting
import com.woka.feature.editor.tools.crop.handleAt
import com.woka.feature.editor.tools.crop.movedBy
import com.woka.feature.editor.tools.crop.resizedBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The crop rect's arithmetic, with no window and no pointer (ME-109).
 *
 * One rule holds every case below: **a rect never leaves the frame and never goes
 * under the minimum**, whatever order of drags produced it. The overlay is gestures;
 * every decision about where a rect ends up is here, where it can be asserted.
 */
class CropMathTest {

    private val frame = IntSize(1000, 800)

    @Test
    fun `an aspect fit is centred and inside the frame`() {
        val square = cropFitting(frame, CropAspect.Square.ratioIn(frame))

        assertEquals(800, square.width)
        assertEquals(800, square.height)
        assertEquals(100, square.left, "a centred square did not sit centred")
        assertEquals(0, square.top)
    }

    @Test
    fun `a wide aspect is limited by the frame's width`() {
        val wide = cropFitting(frame, CropAspect.SixteenNine.ratioIn(frame))

        assertEquals(1000, wide.width)
        assertEquals(562, wide.height)
        assertTrue(wide.bottom <= frame.height)
    }

    /** Free is the whole picture, which is what the tool opens on. */
    @Test
    fun `no ratio fits the whole frame`() {
        assertEquals(CropRect.of(frame), cropFitting(frame, CropAspect.Free.ratioIn(frame)))
    }

    @Test
    fun `a body drag stops at the frame rather than leaving it`() {
        val rect = CropRect(100, 100, 200, 200)

        assertEquals(CropRect(300, 250, 200, 200), rect.movedBy(200, 150, frame))
        // Far past the corner: it stops flush, and keeps its size.
        val shoved = rect.movedBy(9000, 9000, frame)
        assertEquals(CropRect(800, 600, 200, 200), shoved)
    }

    @Test
    fun `a handle dragged past the opposite edge stops at the minimum`() {
        val rect = CropRect(100, 100, 400, 400)
        val squashed = rect.resizedBy(CropHandle.Left, dx = 9000, dy = 0, frame = frame)

        assertEquals(32, squashed.width, "the rect went under the minimum side")
        assertEquals(500, squashed.right, "the edge that was not dragged moved")
    }

    @Test
    fun `dragging one edge leaves the other three where they were`() {
        val rect = CropRect(100, 100, 400, 400)
        val pulled = rect.resizedBy(CropHandle.Right, dx = 120, dy = 0, frame = frame)

        assertEquals(CropRect(100, 100, 520, 400), pulled)
    }

    @Test
    fun `a corner drag moves both of its edges and neither of the others`() {
        val rect = CropRect(100, 100, 400, 400)
        val pulled = rect.resizedBy(CropHandle.BottomRight, dx = 100, dy = 50, frame = frame)

        assertEquals(CropRect(100, 100, 500, 450), pulled)
    }

    /** A locked ratio moves the axis the handle does not drag. */
    @Test
    fun `a drag with an aspect locked reshapes both axes`() {
        val rect = CropRect(0, 0, 400, 400)
        val ratio = CropAspect.Square.ratioIn(frame)
        val pulled = rect.resizedBy(CropHandle.Right, dx = 100, dy = 0, frame = frame, ratio = ratio)

        assertEquals(500, pulled.width)
        assertEquals(500, pulled.height, "the locked axis did not follow")
    }

    /** And it stays inside the frame, shrinking the leading axis rather than the shape. */
    @Test
    fun `a locked drag that would overflow keeps its ratio`() {
        val rect = CropRect(0, 600, 1000, 200)
        val ratio = CropAspect.Square.ratioIn(frame)
        val pulled = rect.resizedBy(CropHandle.Right, dx = 0, dy = 0, frame = frame, ratio = ratio)

        assertEquals(pulled.width, pulled.height, "the ratio broke at the frame edge")
        assertTrue(pulled.bottom <= frame.height && pulled.right <= frame.width)
    }

    @Test
    fun `a rect larger than the frame is clamped to it`() {
        val huge = CropRect(-50, -50, 5000, 5000).clampedTo(frame)

        assertEquals(CropRect(0, 0, 1000, 800), huge)
    }

    @Test
    fun `the hit test finds a corner, an edge, the body and nothing`() {
        val rect = CropRect(100, 100, 400, 400)

        assertEquals(CropHandle.TopLeft, rect.handleAt(104, 96, tolerance = 10))
        assertEquals(CropHandle.BottomRight, rect.handleAt(498, 502, tolerance = 10))
        assertEquals(CropHandle.Right, rect.handleAt(502, 300, tolerance = 10))
        assertEquals(CropHandle.Top, rect.handleAt(300, 98, tolerance = 10))
        assertEquals(CropHandle.Body, rect.handleAt(300, 300, tolerance = 10))
        assertNull(rect.handleAt(900, 700, tolerance = 10))
    }

    /** At a corner both edge tests pass, and grabbing a corner must resize both axes. */
    @Test
    fun `a corner wins over the edges that meet there`() {
        val rect = CropRect(100, 100, 400, 400)

        assertEquals(CropHandle.TopLeft, rect.handleAt(100, 100, tolerance = 20))
        assertTrue(CropHandle.TopLeft.isCorner)
    }

    /**
     * A corner is the hardest target on screen — and the bottom two sit where the
     * picture meets the inspector — so they answer from further away than an edge does.
     */
    @Test
    fun `a corner is caught from further away than an edge`() {
        val rect = CropRect(100, 100, 400, 400)

        // Thirty out from the corner: too far for an edge, within the corner's reach.
        assertEquals(
            CropHandle.TopLeft,
            rect.handleAt(70, 70, tolerance = 10, cornerTolerance = 40)
        )
        // The same distance from an edge, away from any corner, is still nothing.
        assertNull(rect.handleAt(300, 60, tolerance = 10, cornerTolerance = 40))
    }

    /** A picture smaller than the minimum must still be croppable at all. */
    @Test
    fun `a tiny frame lowers the minimum rather than refusing to resize`() {
        val tiny = IntSize(20, 20)
        val rect = CropRect(0, 0, 20, 20).resizedBy(CropHandle.Left, dx = 900, dy = 0, frame = tiny)

        assertTrue(rect.width >= 1, "the rect collapsed")
        assertTrue(rect.right <= tiny.width)
    }
}
