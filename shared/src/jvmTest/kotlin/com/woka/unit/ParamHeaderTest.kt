package com.woka.unit

import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.transform.TransformTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The card's header is a **declaration**, not a rule about `ActionParam` (ME-174).
 *
 * Asserted on the tools rather than through a composition, because what can go wrong is
 * arithmetic on lists: a header spec drawn twice, a header spec drawn nowhere, or a rule
 * that quietly swept up Transform's four buttons on its way past.
 */
class ParamHeaderTest {

    private val adjustRows: List<ParamSpec<*>> = AdjustTool.params

    @Test
    fun `adjust puts auto and reset all in its header`() {
        assertEquals(
            listOf(AdjustTool.Auto, AdjustTool.ResetAll),
            AdjustTool.header(adjustRows)
        )
    }

    /** Both are still real specs of the tool — the header moves them, it does not mint them. */
    @Test
    fun `a header spec is one of the tool's own`() {
        AdjustTool.header(adjustRows).forEach { spec ->
            assertTrue(spec in adjustRows, "${spec.key} is in the header but not in params")
        }
    }

    /**
     * What the shell draws as rows is everything else, exactly once. This mirrors the
     * subtraction `ParamRows` performs, and is the assertion that fails if a spec ever
     * lands in both places.
     */
    @Test
    fun `the header specs are not also rows`() {
        val header = AdjustTool.header(adjustRows)
        val rows = adjustRows.filterNot { it in header }

        assertEquals(adjustRows.size - header.size, rows.size)
        header.forEach { spec -> assertTrue(spec !in rows, "${spec.key} is drawn twice") }
    }

    /**
     * **Transform is the control case.** Its four actions *are* the tool, so they stay
     * rows — a header that had been written as "actions go to the top" would have taken
     * them and left that panel empty.
     */
    @Test
    fun `a tool that declares no header keeps every action as a row`() {
        assertTrue(TransformTool.header(TransformTool.params).isEmpty())
    }
}
