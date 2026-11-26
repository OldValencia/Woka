package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.image.straightened
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The straightened frame (RD-205): the largest picture of the frame's shape that the turned
 * one still fills, at the source's own pixel size — the author's "real pixels only".
 */
class StraightenFrameTest {

    @Test
    fun `a level picture is the identity at its own size`() {
        val stage = straightened(IntSize(640, 480), 0f)

        assertEquals(IntSize(640, 480), stage.outputSize)
        assertEquals(Offset(17f, 23f), stage.matrix.map(Offset(17f, 23f)))
    }

    @Test
    fun `ten degrees keeps 4818 by 3212 of 6000 by 4000, either way round`() {
        assertEquals(IntSize(4818, 3212), straightened(IntSize(6000, 4000), 10f).outputSize)
        assertEquals(IntSize(4818, 3212), straightened(IntSize(6000, 4000), -10f).outputSize)
        assertEquals(IntSize(3212, 4818), straightened(IntSize(4000, 6000), 10f).outputSize)
    }

    @Test
    fun `the centre stays the centre`() {
        val stage = straightened(IntSize(6000, 4000), 7.5f)
        val out = stage.outputSize!!

        val centre = stage.matrix.map(Offset(3000f, 2000f))
        assertEquals(out.width / 2f, centre.x, 0.01f)
        assertEquals(out.height / 2f, centre.y, 0.01f)
    }

    /**
     * Every corner of the output, taken back through the turn, lands on the picture — so the
     * saved file has no transparent corner. Floored sizes are what keeps this true.
     */
    @Test
    fun `every output corner falls inside the source`() {
        listOf(IntSize(6000, 4000), IntSize(4000, 6000), IntSize(640, 480), IntSize(1920, 1080)).forEach { frame ->
            listOf(-45f, -12.3f, 0.5f, 10f, 45f).forEach { degrees ->
                val stage = straightened(frame, degrees)
                val back = Matrix(stage.matrix.values.copyOf()).apply { invert() }
                val out = stage.outputSize!!
                corners(out).forEach { corner ->
                    val at = back.map(corner)
                    val inside = at.x in -0.01f..frame.width + 0.01f && at.y in -0.01f..frame.height + 0.01f
                    assertTrue(inside, "$frame at $degrees°: $corner maps to $at")
                }
            }
        }
    }

    private fun corners(size: IntSize): List<Offset> {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        return listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h))
    }
}
