package com.woka.unit

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.canvasDocumentFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holding the peek button empties what the canvas draws, and nothing else (ME-173).
 *
 * Asserted on `canvasDocumentFor` because that is the whole of the mechanism: the button
 * sets a flag, this function answers an empty document, and `EditorCanvasHost` measures
 * the frame from `engine.drawnSize(document)` — so the geometry follows without anything
 * else being told about it.
 */
class EditorPeekTest {

    private val edited = EditDocument(
        ops = listOf(ToolOp("crop", ParamSet()), ToolOp("adjust", ParamSet()))
    )

    /** The whole original: geometry included, which is what the author chose. */
    @Test
    fun `peeking empties the document the canvas draws`() {
        assertTrue(canvasDocumentFor(null, edited, peeking = true).isEmpty)
    }

    /**
     * **The document itself is never touched.** A peek that mutated it would put a crop
     * into the undo stack for having looked at the picture.
     */
    @Test
    fun `peeking leaves the document it was given alone`() {
        canvasDocumentFor(null, edited, peeking = true)
        assertEquals(2, edited.ops.size)
    }

    /** Released, the canvas is handed exactly what it had before. */
    @Test
    fun `not peeking changes nothing`() {
        assertEquals(edited, canvasDocumentFor(null, edited, peeking = false))
        assertEquals(edited, canvasDocumentFor(null, edited))
    }
}
