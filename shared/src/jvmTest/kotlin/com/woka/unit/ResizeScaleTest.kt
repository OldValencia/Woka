package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.tools.resize.resizeFootnote
import com.woka.feature.editor.tools.resize.resizedFrame
import com.woka.feature.editor.tools.resize.scaleForLongEdge
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The resize arithmetic (ME-110), with no composable in sight.
 *
 * "2560 on a 4000×3000" is a number nobody should have to read out of a UI file, which
 * is the whole reason it lives in a function of two plain values.
 */
class ResizeScaleTest {

    private val landscape = IntSize(4000, 3000)
    private val portrait = IntSize(3000, 4000)

    /** The **long** edge, so a picture and its rotation resolve to the same proportion. */
    @Test
    fun `a long-edge target is the same proportion either way up`() {
        val wide = scaleForLongEdge(2560, landscape)
        val tall = scaleForLongEdge(2560, portrait)

        assertEquals(wide, tall, "landscape and portrait disagreed about the long edge")
        assertTrue(abs(wide - 64f) < 0.01f, "2560 of 4000 is 64 %, got $wide")
    }

    /** This tool makes pictures smaller; a target beyond the source is already met. */
    @Test
    fun `a target larger than the source clamps to full size`() {
        assertEquals(100f, scaleForLongEdge(8000, landscape))
        assertEquals(100f, scaleForLongEdge(4000, landscape))
    }

    @Test
    fun `a frame with no size answers full rather than dividing by zero`() {
        assertEquals(100f, scaleForLongEdge(2560, IntSize.Zero))
        assertEquals(100f, scaleForLongEdge(0, landscape))
    }

    @Test
    fun `a resized frame is the percentage of each side, and never zero`() {
        assertEquals(IntSize(2000, 1500), resizedFrame(50f, landscape))
        assertEquals(IntSize(1, 1), resizedFrame(5f, IntSize(4, 4)))
    }

    @Test
    fun `the footnote reads source to target, and says nothing at full size`() {
        assertEquals("4000 × 3000 → 2000 × 1500", resizeFootnote(50f, landscape))
        assertNull(resizeFootnote(100f, landscape), "a picture at full size is not resized")
        assertNull(resizeFootnote(50f, IntSize.Zero))
    }
}
