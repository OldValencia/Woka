package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.shell.OutputSizeState
import com.woka.feature.editor.tools.resize.CUSTOM_KEY
import com.woka.feature.editor.tools.resize.ORIGINAL_KEY
import com.woka.feature.editor.tools.resize.SizeChoice
import com.woka.feature.editor.tools.resize.SizeLimits
import com.woka.feature.editor.tools.resize.photoSizePresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The size group's behaviour without a screen (RD-401, `dialogs.js` `sizedOutputDialog`):
 * presets fill the fields, typing selects `Custom`, `Keep proportions` links the sides.
 */
class OutputSizeStateTest {

    private val frame = IntSize(4000, 3000)

    private fun state(saved: SizeChoice = SizeChoice.Original) =
        OutputSizeState(frame, photoSizePresets(frame), SizeLimits.Photo, saved)

    @Test
    fun `it opens at Original with the frame in the fields`() {
        val size = state()

        assertEquals(ORIGINAL_KEY, size.selected)
        assertEquals("4000" to "3000", size.width to size.height)
        assertEquals(SizeChoice.Original, size.choice)
        assertNull(size.exportSize, "Original sized a copy")
    }

    @Test
    fun `a preset fills the fields, and typing in either selects Custom`() {
        val size = state()
        size.pick("50")
        assertEquals("2000" to "1500", size.width to size.height)
        assertEquals(SizeChoice.Percent(50f), size.choice)

        size.type(1, "600")
        assertEquals(CUSTOM_KEY, size.selected)
        assertEquals("800" to "600", size.width to size.height)
        assertEquals(SizeChoice.Custom(800, 600, keepProportions = true), size.choice)
    }

    @Test
    fun `unlinked fields are independent, and relinking keeps the last edited side`() {
        val size = state()
        size.lock(false)
        size.type(0, "1000")
        size.type(1, "1000")
        assertEquals(SizeChoice.Custom(1000, 1000, keepProportions = false), size.choice)

        size.lock(true)
        assertEquals("1333" to "1000", size.width to size.height)
    }

    @Test
    fun `a saved size reopens as it was left`() {
        assertEquals("25", state(SizeChoice.Percent(25f)).selected)
        val custom = state(SizeChoice.Custom(900, 300, keepProportions = false))
        assertEquals(CUSTOM_KEY, custom.selected)
        assertEquals("900" to "300", custom.width to custom.height)
        assertFalse(custom.keepProportions)
    }

    /** The old slider's 37 % is no preset: it shows as pixels and, untouched, saves as 37 %. */
    @Test
    fun `a legacy percentage opens as Custom and is kept when nothing is touched`() {
        val legacy = state(SizeChoice.Percent(37f))
        assertEquals(CUSTOM_KEY, legacy.selected)
        assertEquals("1480" to "1110", legacy.width to legacy.height)
        assertTrue(legacy.keepProportions)
        assertEquals(SizeChoice.Percent(37f), legacy.choice)

        legacy.type(0, "1480")
        assertEquals(SizeChoice.Custom(1480, 1110, keepProportions = true), legacy.choice)
    }

    @Test
    fun `pixels past the renderer are refused, a preset never`() {
        val size = state()
        size.type(0, "20000")
        assertFalse(size.valid)
        size.type(0, "")
        assertFalse(size.valid)
        size.pick(ORIGINAL_KEY)
        assertTrue(size.valid)
    }
}
