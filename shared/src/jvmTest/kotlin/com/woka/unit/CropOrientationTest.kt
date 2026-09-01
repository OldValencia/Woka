package com.woka.unit

import com.woka.feature.editor.core.BoolValue
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.tools.crop.CropOrientation
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.crop.cropIsPortrait
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The migration `Portrait` needed when it stopped being a boolean (ME-144).
 *
 * **This is the test the ticket exists to have.** The key on the wire did not change, so
 * a crop saved before the conversion still holds `BoolValue` under it — and
 * `ChoiceParam.decode` answers null on a type mismatch, which `ParamSet` turns into the
 * default. Without the branch in `cropIsPortrait` every turned ratio would come back
 * upright, silently, on a photograph the user had already finished with.
 */
class CropOrientationTest {

    private fun documentWith(params: ParamSet) =
        EditDocument(ops = listOf(ToolOp(CropTool.manifest.id, params)))

    @Test
    fun `a crop saved before the conversion keeps its orientation`() {
        val old = ParamSet(values = mapOf(CropTool.Portrait.key to BoolValue(true)))
        assertTrue(documentWith(old).cropIsPortrait(), "an older document lost its turn")
    }

    @Test
    fun `an older landscape crop stays landscape`() {
        val old = ParamSet(values = mapOf(CropTool.Portrait.key to BoolValue(false)))
        assertFalse(documentWith(old).cropIsPortrait())
    }

    @Test
    fun `the choice is read as itself once it has been written as one`() {
        val turned = ParamSet().with(CropTool.Portrait, CropOrientation.Portrait)
        assertTrue(documentWith(turned).cropIsPortrait())

        val flat = ParamSet().with(CropTool.Portrait, CropOrientation.Landscape)
        assertFalse(documentWith(flat).cropIsPortrait())
    }

    /** Nothing written at all is a crop that was never turned. */
    @Test
    fun `an untouched crop is landscape`() {
        assertFalse(documentWith(ParamSet()).cropIsPortrait())
        assertFalse(EditDocument().cropIsPortrait())
    }
}
