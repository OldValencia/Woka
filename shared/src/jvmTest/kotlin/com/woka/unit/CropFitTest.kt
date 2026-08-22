package com.woka.unit

import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import com.woka.feature.editor.tools.crop.CropAspect
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.fittedTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What **choosing** a ratio does to a crop that already exists (ME-109).
 *
 * This is the half that was missing when the tool first shipped: the aspect was stored
 * and only consulted the next time a handle was dragged, so pressing `1:1` lit the chip
 * up and moved nothing. A control that does not visibly do anything is a broken one.
 */
class CropFitTest {

    private val frame = IntSize(1000, 800)

    /** Area is what is held, so a 400×200 becomes the square of the same area. */
    @Test
    fun `a square keeps the centre and the area`() {
        val wide = CropRect(200, 300, 400, 200)
        val square = wide.fittedTo(CropAspect.Square.ratioIn(frame)!!, frame)

        assertEquals(square.width, square.height, "it is not square")
        assertTrue(abs(square.width - 283) <= 1, "the area was not held: ${square.width}")
        // Same centre, to the pixel integer division costs on an odd side.
        assertTrue(abs((wide.left + wide.width / 2) - (square.left + square.width / 2)) <= 1)
        assertTrue(abs((wide.top + wide.height / 2) - (square.top + square.height / 2)) <= 1)
    }

    @Test
    fun `a ratio never reaches outside the frame`() {
        val edge = CropRect(900, 700, 100, 100)
        val wide = edge.fittedTo(CropAspect.SixteenNine.ratioIn(frame)!!, frame)

        assertTrue(wide.right <= frame.width, "it ran off the right edge")
        assertTrue(wide.bottom <= frame.height, "it ran off the bottom")
        assertTrue(wide.left >= 0 && wide.top >= 0)
    }

    @Test
    fun `a rect already at the ratio is left exactly as it is`() {
        val square = CropRect(100, 100, 300, 300)

        assertEquals(square, square.fittedTo(CropAspect.Square.ratioIn(frame)!!, frame))
    }

    /** `Original` follows the picture, so on a 5:4 frame it is 5:4 and not a constant. */
    @Test
    fun `Original is the frame's own shape`() {
        val fitted = CropRect(0, 0, 800, 800).fittedTo(CropAspect.Original.ratioIn(frame)!!, frame)

        val wanted = frame.width.toFloat() / frame.height
        val got = fitted.width.toFloat() / fitted.height
        assertTrue(abs(wanted - got) < 0.02f, "Original gave $got, the frame is $wanted")
    }

    /**
     * **The round trip has to come back.** Taking the shorter side each time lost a
     * little on every press, so flipping between `1:1` and `Original` walked the
     * rectangle down to the minimum a few presses later — reported by hand, 2026-08-22.
     */
    @Test
    fun `switching between two ratios does not shrink the rectangle`() {
        val square = CropAspect.Square.ratioIn(frame)!!
        val original = CropAspect.Original.ratioIn(frame)!!
        var rect = CropRect(300, 200, 240, 240)
        val area = rect.width * rect.height

        repeat(20) {
            rect = rect.fittedTo(original, frame).fittedTo(square, frame)
        }

        // A pixel of rounding either way is fine; a slide to the floor is not.
        assertTrue(
            abs(rect.width * rect.height - area) < area * 0.05,
            "twenty round trips went from $area to ${rect.width * rect.height}"
        )
        assertTrue(rect.width > 200, "the rectangle walked down to ${rect.width}")
    }

    /** Free is the absence of a lock: it has no ratio to fit to. */
    @Test
    fun `Free declares no ratio at all`() {
        assertEquals(null, CropAspect.Free.ratioIn(frame))
    }

    @Test
    fun `flipping a ratio turns it on its side`() {
        val wide = CropAspect.SixteenNine.ratioIn(frame)!!
        val tall = CropAspect.SixteenNine.flippedIn(frame)!!

        assertTrue(wide > 1f && tall < 1f, "the flip did not turn the ratio over")
        assertEquals(1f, wide * tall, 0.001f)
    }
}
