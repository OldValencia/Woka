package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.shell.EditorCanvasGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How far the editing canvas zooms, at both ends.
 *
 * Split from `EditorCanvasGeometryTest` under the size mandate: that file is about the
 * two matrices and the pan clamp, and this is about the range they are computed over.
 *
 * **The range is the viewer's, not the editor's own.** The ceiling was settled on
 * 2026-08-18 (1000 % of native, because a magnifier that will not magnify is worse than
 * a soft one) and the floor on 2026-08-21, when the editor turned out to be the one
 * panel where a picture could be enlarged but never shrunk. Both now come from the same
 * place the viewer reads them, so the same file zooms to the same limits in both.
 */
class EditorCanvasZoomRangeTest {

    @Test
    fun `zoom stops at ten source pixels per screen pixel`() {
        // 1000x500 fitted into 500x900 draws at 0.5, so 1000 % of native is 20x Fit.
        val g = EditorCanvasGeometry(IntSize(1000, 500), IntSize(500, 900))
        assertEquals(20f, g.maxZoom)

        val zoomed = EditorCanvasGeometry(IntSize(1000, 500), IntSize(500, 900), zoom = 99f)
        // Asked for 99x, given 1000 % of native — the same ceiling the viewer has.
        assertEquals(10f, zoomed.scale)
    }

    /**
     * **Fit is not the floor.** It was, and it made the editor the one panel where a
     * picture could be enlarged but never shrunk. The number is the viewer's own
     * `zoomBounds`, so the same file zooms out to the same place in both.
     */
    @Test
    fun `zoom goes below Fit, as far out as the viewer allows`() {
        val g = EditorCanvasGeometry(IntSize(1000, 500), IntSize(500, 900))

        // Fit draws this at 50 % of native and the viewer's floor is a tenth of that,
        // which is a tenth of Fit on this scale.
        assertEquals(0.1f, g.minZoom, 0.001f)
        assertEquals(0.05f, EditorCanvasGeometry(IntSize(1000, 500), IntSize(500, 900), zoom = 0.1f).scale, 0.001f)
        // Past the floor is clamped, not refused.
        assertEquals(g.minZoom * g.fitScale, EditorCanvasGeometry(IntSize(1000, 500), IntSize(500, 900), zoom = 0.001f).scale, 0.001f)
    }

    /**
     * A tenth of Fit is not the whole rule: once Fit is past 1000 % of native — a tiny
     * picture on a large canvas — the viewer's bounds stop the floor at 1:1 instead,
     * so the smallest it goes is its own pixels rather than a twentieth of them.
     */
    @Test
    fun `a picture the canvas hugely enlarges stops shrinking at its own pixels`() {
        val g = EditorCanvasGeometry(IntSize(20, 20), IntSize(400, 400))

        assertEquals(20f, g.fitScale, 0.001f)
        assertEquals(1f, g.minZoom * g.fitScale, 0.001f)
    }

    /** Fit itself must always be reachable, whatever the floor works out to. */
    @Test
    fun `the floor never rises past Fit`() {
        listOf(
            IntSize(1000, 500) to IntSize(500, 900),
            IntSize(100, 100) to IntSize(400, 400),
            IntSize(4000, 3000) to IntSize(120, 90)
        ).forEach { (source, canvas) ->
            val g = EditorCanvasGeometry(source, canvas)
            assertTrue(g.minZoom <= 1f, "the floor sat above Fit for $source in $canvas")
            assertTrue(g.minZoom > 0f, "the floor collapsed to zero for $source in $canvas")
        }
    }

    @Test
    fun `a picture the canvas already enlarges keeps the same ceiling`() {
        // Fit draws it fourfold, so there are 2.5 magnifications left, not 10.
        val g = EditorCanvasGeometry(IntSize(100, 100), IntSize(400, 400))
        assertEquals(2.5f, g.maxZoom)
        assertEquals(10f, EditorCanvasGeometry(IntSize(100, 100), IntSize(400, 400), zoom = 8f).scale)
    }
}
