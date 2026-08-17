package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.image.derivedFrame
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arithmetic a rotation cannot do for itself.
 *
 * This is the one piece of ME-105 that can be wrong by exactly 90° and still look
 * plausible in every screenshot, so it is pinned here rather than trusted.
 */
class DerivedFrameTest {

    private val frame = IntSize(640, 480)

    private fun rotate(degrees: Float) = Matrix().apply { rotateZ(degrees) }

    @Test
    fun `a quarter turn swaps the axes`() {
        val (_, size) = derivedFrame(frame, rotate(90f))
        assertEquals(IntSize(480, 640), size)
    }

    @Test
    fun `a quarter turn the other way swaps them too`() {
        val (_, size) = derivedFrame(frame, rotate(-90f))
        assertEquals(IntSize(480, 640), size)
    }

    @Test
    fun `a flip leaves the frame alone`() {
        val (_, horizontal) = derivedFrame(frame, Matrix().apply { scale(x = -1f, y = 1f) })
        val (_, vertical) = derivedFrame(frame, Matrix().apply { scale(x = 1f, y = -1f) })
        assertEquals(frame, horizontal)
        assertEquals(frame, vertical)
    }

    @Test
    fun `the normalised matrix puts the picture back at the origin`() {
        // Without this a 90° turn about the origin leaves the whole picture in
        // negative x, off the canvas and out of the saved file.
        listOf(90f, -90f, 180f).forEach { degrees ->
            val (matrix, size) = derivedFrame(frame, rotate(degrees))
            val corners = listOf(
                matrix.map(Offset(0f, 0f)),
                matrix.map(Offset(640f, 0f)),
                matrix.map(Offset(640f, 480f)),
                matrix.map(Offset(0f, 480f))
            )
            assertTrue(corners.all { it.x >= -0.01f && it.y >= -0.01f }, "$degrees° went negative")
            assertTrue(
                abs(corners.minOf { it.x }) < 0.01f && abs(corners.minOf { it.y }) < 0.01f,
                "$degrees° did not land flush against the origin"
            )
            assertTrue(corners.all { it.x <= size.width + 0.01f && it.y <= size.height + 0.01f })
        }
    }

    @Test
    fun `a degenerate matrix still yields a drawable frame`() {
        // A zero-sized surface is what the save path would hand to Skia.
        val (_, size) = derivedFrame(frame, Matrix().apply { scale(x = 0f, y = 0f) })
        assertTrue(size.width >= 1 && size.height >= 1)
    }
}
