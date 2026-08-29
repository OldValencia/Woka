package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.shell.EditorCanvasGeometry
import com.woka.feature.editor.shell.panForZoomAbout
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
    fun `a picture that fits can still be dragged half a canvas`() {
        val g = EditorCanvasGeometry(IntSize(1000, 500), IntSize(500, 900), pan = Offset(400f, 400f))
        // Until 2026-08-28 this clamped to zero and the picture stayed centred however
        // hard it was dragged. The floor is half the canvas, so 400 across is cut to
        // 250 and 400 down is inside 450 and survives.
        assertEquals(Offset(250f, 400f), g.clampedPan)
    }

    @Test
    fun `a magnified picture pans to its own edges, or half a canvas, whichever is further`() {
        val g = EditorCanvasGeometry(
            IntSize(1000, 500), IntSize(500, 900), zoom = 2f, pan = Offset(9000f, 9000f)
        )
        // Drawn 1000x500 in a 500x900 canvas. Across: 250 of overflow, which is also
        // half the canvas. Down: nothing overflows, so the floor of 450 is the reach.
        assertEquals(250f, g.clampedPan.x)
        assertEquals(450f, g.clampedPan.y)
    }

    @Test
    fun `zooming keeps the point under the cursor under the cursor`() {
        // Both axes must overflow for the focus to be holdable on both. 1000x1000
        // fitted into 400x400 draws at 0.4, so 2.5x is 100 % and overflows each way.
        val source = IntSize(1000, 1000)
        val canvas = IntSize(400, 400)
        val g = EditorCanvasGeometry(source, canvas)
        val focus = Offset(120f, 310f)
        val sourceUnderCursor = g.canvasToSource.map(focus)

        val next = EditorCanvasGeometry(source, canvas, zoom = 2.5f, pan = g.panForZoomAbout(focus, 2.5f))

        // The whole point of zooming about the cursor: the detail pointed at does not
        // slide away. Without it the picture magnifies about its centre instead.
        val after = next.sourceToCanvas.map(sourceUnderCursor)
        assertTrue(
            abs(after.x - focus.x) < 0.5f && abs(after.y - focus.y) < 0.5f,
            "the focus moved from $focus to $after"
        )
    }

    @Test
    fun `the cursor is held on an axis that does not overflow`() {
        // 1000x500 at 100 % in a 500x900 canvas overflows across and fits down. Until
        // 2026-08-28 the vertical axis had no slack, so the focus could be held on x
        // and never on y — the picture snapped back to centre under the cursor. The
        // floor of half a canvas is 450 here, the pan it wants is -50, so both axes
        // now hold.
        val source = IntSize(1000, 500)
        val canvas = IntSize(500, 900)
        val g = EditorCanvasGeometry(source, canvas)
        val focus = Offset(120f, 500f)
        val sourceUnderCursor = g.canvasToSource.map(focus)

        val next = EditorCanvasGeometry(source, canvas, zoom = 2f, pan = g.panForZoomAbout(focus, 2f))
        val after = next.sourceToCanvas.map(sourceUnderCursor)

        assertTrue(abs(after.x - focus.x) < 0.5f, "x should have been held, went to ${after.x}")
        assertTrue(abs(after.y - focus.y) < 0.5f, "y should have been held, went to ${after.y}")
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
