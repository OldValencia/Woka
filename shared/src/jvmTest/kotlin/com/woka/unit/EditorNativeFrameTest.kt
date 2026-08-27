package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.image.NATIVE_HOLD_MAX_PX
import com.woka.feature.editor.engine.image.canHoldNative
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two budgets that decide whether the canvas may hold a picture at native
 * resolution (ME-116), asserted against the predicate itself — nothing here decodes,
 * opens or allocates anything, which is what keeps a memory rule in `unit/`.
 *
 * The rule exists because a native hold is `w × h × 4` bytes and is deliberately
 * outside `ImageBitmapCache`: 96 MB for 24 MP, and **384 MB** for the 100 MP case
 * ME-010 measured, against a 256 MB cache budget it would not fit into anyway.
 */
class EditorNativeFrameTest {

    @Test
    fun `a forty megapixel source is held`() {
        // 40 MP exactly, with its longest edge inside 8192 — the ceiling, not past it.
        assertTrue(canHoldNative(IntSize(8000, 5000)), "the budget refused the size it is set to")
    }

    @Test
    fun `a source past the area budget is refused on its area alone`() {
        // 64 MP — 256 MB — while both edges are comfortably inside 8192, so only the
        // area rule can be what turns it away.
        val square = IntSize(8000, 8000)
        assertTrue(square.width <= 8192 && square.height <= 8192, "the fixture must pass the edge budget")
        assertFalse(canHoldNative(square))
    }

    @Test
    fun `the hundred megapixel case is refused`() {
        assertFalse(canHoldNative(IntSize(11648, 8736)), "384 MB of pixels was admitted")
    }

    @Test
    fun `a panorama is refused on its edge rather than its area`() {
        // 10.8 MP: it passes the area budget, and would be admitted without a second
        // rule — while Skia would be sampling a 12000 px row into a canvas a thousand
        // pixels wide.
        val panorama = IntSize(12000, 900)
        val area = panorama.width.toLong() * panorama.height
        assertTrue(area < NATIVE_HOLD_MAX_PX, "the fixture must pass the area budget")
        assertFalse(canHoldNative(panorama))
    }

    @Test
    fun `an unmeasured source is refused`() {
        // `sourceSize` is read lazily from the decoder, so a canvas composed before it
        // has answered asks with zeroes rather than waiting.
        assertFalse(canHoldNative(IntSize.Zero))
    }
}
