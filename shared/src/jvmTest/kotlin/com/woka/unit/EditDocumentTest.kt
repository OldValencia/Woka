package com.woka.unit

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val Amount = FloatParam("amount", "Amount", 0f..1f, default = 0f)

private fun op(toolId: String, amount: Float) =
    ToolOp(toolId, ParamSet().with(Amount, amount))

/** Cardinality is the whole behaviour here: what replaces, what accumulates (§3.4). */
class EditDocumentTest {

    @Test
    fun `Single replaces the tool's previous operation`() {
        val document = EditDocument()
            .withPending(op("adjust", 0.2f))
            .commitPending(ToolCardinality.Single)
            .withPending(op("adjust", 0.8f))
            .commitPending(ToolCardinality.Single)

        assertEquals(1, document.ops.size)
        assertEquals(0.8f, document.ops.single().params[Amount])
    }

    @Test
    fun `Single replaces only its own tool`() {
        val document = EditDocument()
            .withPending(op("crop", 0.5f))
            .commitPending(ToolCardinality.Single)
            .withPending(op("adjust", 0.2f))
            .commitPending(ToolCardinality.Single)
            .withPending(op("adjust", 0.9f))
            .commitPending(ToolCardinality.Single)

        assertEquals(listOf("crop", "adjust"), document.ops.map { it.toolId })
        assertEquals(1, document.opsOf("adjust").size)
    }

    @Test
    fun `Repeating appends`() {
        val document = EditDocument()
            .withPending(op("rotate", 1f))
            .commitPending(ToolCardinality.Repeating)
            .withPending(op("rotate", 1f))
            .commitPending(ToolCardinality.Repeating)

        assertEquals(2, document.opsOf("rotate").size)
    }

    @Test
    fun `committing with nothing pending changes nothing`() {
        val document = EditDocument()
        assertEquals(document, document.commitPending(ToolCardinality.Single))
    }

    @Test
    fun `a pending operation leaves ops alone until it is committed`() {
        val pending = EditDocument().withPending(op("adjust", 0.4f))
        assertTrue(pending.ops.isEmpty())
        assertEquals(0.4f, pending.pending?.params?.get(Amount))
        // …and dropping it is not the same as applying it.
        assertNull(pending.clearPending().pending)
        assertTrue(pending.clearPending().ops.isEmpty())
    }

    @Test
    fun `isEmpty counts a pending operation`() {
        assertTrue(EditDocument().isEmpty)
        assertTrue(!EditDocument().withPending(op("adjust", 0.1f)).isEmpty)
    }
}
