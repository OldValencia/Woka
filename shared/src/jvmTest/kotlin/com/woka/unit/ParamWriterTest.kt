package com.woka.unit

import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolInteraction
import com.woka.feature.editor.shell.ParamWriter
import com.woka.feature.editor.shell.paramsOf
import com.woka.support.FixtureParams
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The undo-granularity contract, which is the whole reason `ParamWriter` exists.
 *
 * §3.4 promises a slider cannot mint two hundred undo steps. These are the cases
 * where it could.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParamWriterTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun tool(
        interaction: ToolInteraction,
        cardinality: ToolCardinality = ToolCardinality.Single
    ) = FixtureTool(interaction = interaction, cardinality = cardinality)

    @Test
    fun `an Instant tool commits on every edit`() {
        val t = tool(ToolInteraction.Instant)
        onEditor(listOf(t)) { harness ->
            val writer = ParamWriter(harness.session, t)
            writer.edit(FixtureParams.Flag, true)

            assertTrue(harness.session.canUndo.value)
            assertEquals(1, harness.session.document.value.ops.size)
        }
    }

    @Test
    fun `fifty unsettled edits and one settle are one history entry`() {
        val t = tool(ToolInteraction.Continuous)
        onEditor(listOf(t)) { harness ->
            repeat(50) { step ->
                ParamWriter(harness.session, t)
                    .edit(FixtureParams.Amount, step / 50f, settled = false)
            }
            assertFalse(harness.session.canUndo.value, "a drag must not push history")

            ParamWriter(harness.session, t).settle()

            assertTrue(harness.session.canUndo.value)
            assertEquals(1, harness.session.document.value.ops.size)
            // And the value that survived is the last one dragged, not the first.
            harness.session.undo()
            assertTrue(harness.session.document.value.isEmpty)
        }
    }

    @Test
    fun `an unsettled drag is visible without being undoable`() {
        val t = tool(ToolInteraction.Continuous)
        onEditor(listOf(t)) { harness ->
            ParamWriter(harness.session, t)
                .edit(FixtureParams.Amount, 0.5f, settled = false)

            val document = harness.session.document.value
            // Pending renders exactly as committed would (§3.1) — that is what makes a
            // preview honest — but history has not moved.
            assertEquals(0.5f, paramsOf(document, t)[FixtureParams.Amount])
            assertFalse(harness.session.canUndo.value)
        }
    }

    @Test
    fun `cardinality decides whether two settled edits are one op or two`() {
        val single = tool(ToolInteraction.Instant, ToolCardinality.Single)
        onEditor(listOf(single)) { harness ->
            repeat(2) {
                ParamWriter(harness.session, single)
                    .edit(FixtureParams.Count, it + 2)
            }
            assertEquals(1, harness.session.document.value.ops.size)
        }

        val repeating = tool(ToolInteraction.Instant, ToolCardinality.Repeating)
        onEditor(listOf(repeating)) { harness ->
            repeat(2) {
                ParamWriter(harness.session, repeating)
                    .edit(FixtureParams.Press, true)
            }
            assertEquals(2, harness.session.document.value.ops.size)
        }
    }

    @Test
    fun `a value is clamped to its spec's range on the way in`() {
        val t = tool(ToolInteraction.Instant)
        onEditor(listOf(t)) { harness ->
            ParamWriter(harness.session, t)
                .edit(FixtureParams.Count, 9999)

            // `IntParam.encode` clamps, so the document can never hold a value the
            // control could not have produced.
            assertEquals(10, paramsOf(harness.session.document.value, t)[FixtureParams.Count])
        }
    }
}
