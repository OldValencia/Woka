package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.shell.EditorCanvasGeometry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two matrices the whole canvas rests on.
 *
 * `canvasToSource` is produced by inverting `sourceToCanvas` rather than written out
 * separately, and this is what holds that honest: a hand-written inverse that is
 * *nearly* right produces markup landing a few pixels from the cursor, worse the
 * further from centre — which reads as a gesture bug for a week before anyone looks
 * at the arithmetic.
 */
class EditorCanvasGeometryTest {

    @Test
    fun `a wide picture in a tall box is limited by width`() {
        val g = EditorCanvasGeometry(sourceSize = IntSize(1000, 500), canvasSize = IntSize(500, 900))

        assertEquals(0.5f, g.scale)
        // Centred vertically, flush horizontally.
        assertEquals(0f, g.offset.x)
        assertEquals(325f, g.offset.y)
    }

    @Test
    fun `a tall picture in a wide box is limited by height`() {
        val g = EditorCanvasGeometry(sourceSize = IntSize(500, 1000), canvasSize = IntSize(900, 500))

        assertEquals(0.5f, g.scale)
        assertEquals(325f, g.offset.x)
        assertEquals(0f, g.offset.y)
    }

    @Test
    fun `a picture smaller than the canvas is enlarged`() {
        // An editor exists to show you what you are changing, so Fit scales up.
        val g = EditorCanvasGeometry(sourceSize = IntSize(100, 100), canvasSize = IntSize(400, 400))
        assertEquals(4f, g.scale)
    }

    @Test
    fun `an unmeasured canvas yields scale 1 rather than a division by zero`() {
        val g = EditorCanvasGeometry(sourceSize = IntSize(640, 480), canvasSize = IntSize.Zero)
        assertEquals(1f, g.scale)
    }

    @Test
    fun `the two matrices are inverses at every canvas size`() {
        listOf(
            IntSize(500, 900) to IntSize(1000, 500),
            IntSize(1920, 1080) to IntSize(640, 480),
            IntSize(137, 991) to IntSize(4000, 1000)
        ).forEach { (canvas, source) ->
            val g = EditorCanvasGeometry(sourceSize = source, canvasSize = canvas)
            listOf(Offset.Zero, Offset(17f, 42f), Offset(source.width.toFloat(), source.height.toFloat()))
                .forEach { point ->
                    val roundTrip = g.canvasToSource.map(g.sourceToCanvas.map(point))
                    assertTrue(
                        abs(roundTrip.x - point.x) < 0.01f && abs(roundTrip.y - point.y) < 0.01f,
                        "round trip of $point at $canvas/$source landed at $roundTrip"
                    )
                }
        }
    }
}
