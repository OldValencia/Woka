package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.shell.EditorCanvasGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two coordinate spaces the canvas has, and which one an overlay lives in
 * (2026-08-29, reported by hand as "drag the picture and the outline drifts").
 *
 * `EditorCanvasHost` centres and pans the picture's own box and composes the overlay
 * **inside** it. So the overlay's origin is the picture's top-left, and a matrix that also
 * carries the centring and the pan applies them a second time. These assertions are the
 * difference stated as something a build can fail.
 */
class EditorCanvasSpacesTest {

    private fun geometry(pan: Offset = Offset.Zero, zoom: Float = 1f) = EditorCanvasGeometry(
        sourceSize = IntSize(800, 400),
        canvasSize = IntSize(1000, 1000),
        zoom = zoom,
        pan = pan
    )

    @Test
    fun `the picture space starts at the picture, whatever the pan`() {
        listOf(Offset.Zero, Offset(120f, -80f), Offset(-400f, 250f)).forEach { pan ->
            val at = geometry(pan).sourceToPicture.map(Offset.Zero)
            assertEquals(Offset.Zero, at, "a pan of $pan moved the picture's own origin")
        }
    }

    @Test
    fun `the picture space spans exactly the drawn picture`() {
        val g = geometry(pan = Offset(90f, 40f))
        val corner = g.sourceToPicture.map(Offset(800f, 400f))

        assertEquals(800f * g.scale, corner.x, absoluteTolerance = 0.01f)
        assertEquals(400f * g.scale, corner.y, absoluteTolerance = 0.01f)
    }

    /**
     * And the canvas space still carries the offset, because the pan and zoom gestures
     * sit on the outer box and are handed positions in that space.
     */
    @Test
    fun `the canvas space keeps the centring and the pan`() {
        val g = geometry(pan = Offset(90f, 40f))
        val at = g.sourceToCanvas.map(Offset.Zero)

        assertEquals(g.offset.x, at.x, absoluteTolerance = 0.01f)
        assertEquals(g.offset.y, at.y, absoluteTolerance = 0.01f)
        assertTrue(g.offset != Offset.Zero, "the fixture does not exercise an offset at all")
    }

    /** The inverse is the inverse — the round trip a pointer takes and comes back from. */
    @Test
    fun `picture and source round-trip through each other`() {
        val g = geometry(pan = Offset(-30f, 70f), zoom = 2.5f)
        val source = Offset(123f, 45f)

        val back = g.pictureToSource.map(g.sourceToPicture.map(source))

        assertEquals(source.x, back.x, absoluteTolerance = 0.01f)
        assertEquals(source.y, back.y, absoluteTolerance = 0.01f)
    }
}
