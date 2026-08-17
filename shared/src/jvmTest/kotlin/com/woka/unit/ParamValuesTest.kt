package com.woka.unit

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.paramsOf
import com.woka.support.FixtureParams
import com.woka.support.FixtureTool
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which of three sources a control reads from, and in what order.
 *
 * Getting this wrong is not subtle in production — the knob jumps back under the
 * finger on every drag frame — but it is invisible in a screenshot, so it is pinned
 * here instead.
 */
class ParamValuesTest {

    private val tool = FixtureTool(id = "adjust")

    @Test
    fun `a document that has never heard of the tool reads its defaults`() {
        val params = paramsOf(EditDocument(), tool)
        assertEquals(FixtureParams.Amount.default, params[FixtureParams.Amount])
        assertEquals(FixtureParams.Count.default, params[FixtureParams.Count])
    }

    @Test
    fun `a committed op beats the defaults`() {
        val document = EditDocument(
            ops = listOf(ToolOp("adjust", tool.defaults().with(FixtureParams.Count, 7)))
        )
        assertEquals(7, paramsOf(document, tool)[FixtureParams.Count])
    }

    @Test
    fun `the last committed op wins for a repeating tool`() {
        val document = EditDocument(
            ops = listOf(
                ToolOp("adjust", tool.defaults().with(FixtureParams.Count, 3)),
                ToolOp("adjust", tool.defaults().with(FixtureParams.Count, 8))
            )
        )
        assertEquals(8, paramsOf(document, tool)[FixtureParams.Count])
    }

    @Test
    fun `the operation in flight beats the committed one`() {
        val document = EditDocument(
            ops = listOf(ToolOp("adjust", tool.defaults().with(FixtureParams.Count, 3))),
            pending = ToolOp("adjust", tool.defaults().with(FixtureParams.Count, 9))
        )
        assertEquals(9, paramsOf(document, tool)[FixtureParams.Count])
    }

    @Test
    fun `a pending op belonging to another tool is ignored`() {
        // The case this rule exists for: while the crop overlay drags, the adjustments
        // panel must keep showing adjustment values rather than crop's.
        val document = EditDocument(
            ops = listOf(ToolOp("adjust", tool.defaults().with(FixtureParams.Count, 3))),
            pending = ToolOp("crop", tool.defaults().with(FixtureParams.Count, 9))
        )
        assertEquals(3, paramsOf(document, tool)[FixtureParams.Count])
    }
}
