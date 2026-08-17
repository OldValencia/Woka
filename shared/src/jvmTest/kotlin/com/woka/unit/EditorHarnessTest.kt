package com.woka.unit

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.support.FixtureParams
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Registry, session and fold driven end to end through `onEditor` — **with no
 * production tool in the build** (ME-011's Done when). `EditorModule` declares none
 * until ME-105, so everything the shell will be built on is exercised here or
 * nowhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorHarnessTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun oneToolPerDomain(): List<FixtureTool> =
        ToolDomain.entries.map { FixtureTool(id = it.name.lowercase(), domain = it) }

    @Test
    fun `the registry lists the fixture tool in every domain`() =
        onEditor(oneToolPerDomain()) { harness ->
            assertEquals(
                ToolDomain.entries.map { it.name.lowercase() },
                harness.registry.tools(MediaKind.Image).value.map { it.manifest.id },
                "one fixture per domain, listed in domain order"
            )
        }

    @Test
    fun `the session records the fixture's ops`() {
        val tool = FixtureTool()
        onEditor(listOf(tool)) { harness ->
            harness.apply(ToolOp(tool.manifest.id, tool.defaults().with(FixtureParams.Count, 4)))

            val op = harness.session.document.value.ops.single()
            assertEquals(tool.manifest.id, op.toolId)
            assertEquals(4, op.params[FixtureParams.Count])
            assertTrue(harness.session.canUndo.value)

            harness.undo()
            assertTrue(harness.session.document.value.isEmpty)
        }
    }

    @Test
    fun `the fold reaches the fixture's stage`() {
        val cropped = IntSize(320, 240)
        val tool = FixtureTool(
            domain = ToolDomain.Geometry,
            produce = { RasterStage.Geometry(Matrix(), cropped) }
        )
        onEditor(listOf(tool)) { harness ->
            harness.apply(ToolOp(tool.manifest.id, tool.defaults().with(FixtureParams.Count, 4)))

            val recipe = harness.recipe()
            // Produced *and* consumed: the output size is the fold's only
            // observable proof that this tool's Geometry stage was applied.
            assertEquals(cropped, recipe.outputSize)
            assertTrue(recipe.skipped.isEmpty(), "an enabled, registered tool is never skipped")
            // And the fold asked with the document's values, not with the defaults.
            assertEquals(4, tool.staged.single()[FixtureParams.Count])
        }
    }

    @Test
    fun `a disabled fixture tool is skipped rather than dropped`() {
        val tool = FixtureTool(produce = { RasterStage.Pixels(ColorMatrix()) })
        onEditor(listOf(tool)) { harness ->
            harness.apply(ToolOp(tool.manifest.id, tool.defaults()))
            harness.settings.setEditorToolEnabled(tool.manifest.id, enabled = false)

            val recipe = harness.recipe()
            assertEquals(1, recipe.skipped.size, "the op is reported, never silently lost")
            assertTrue(tool.staged.isEmpty(), "a disabled tool is never asked to stage")
            // Re-enableable with its operations intact is the whole point of §3.4.
            assertEquals(1, harness.session.document.value.ops.size)
        }
    }

    @Test
    fun `a null stage is a no-op rather than a skip`() {
        val tool = FixtureTool()
        onEditor(listOf(tool)) { harness ->
            harness.apply(ToolOp(tool.manifest.id, tool.defaults()))

            val recipe = harness.recipe()
            assertTrue(tool.staged.isNotEmpty(), "the fold must still consult the tool")
            assertTrue(recipe.skipped.isEmpty(), "a no-op configuration is not a missing tool")
            assertEquals(null, recipe.colorFilter)
        }
    }
}
