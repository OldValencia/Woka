package com.woka.unit

import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.skippedOps
import com.woka.feature.editor.shell.skippedToolTitles
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
 * The derivation that feeds the "operations were not applied" notice.
 *
 * It exists separately from `RasterFold` and must agree with it — the fold decides
 * what is drawn, this decides what the user is told, and a disagreement is a notice
 * that lies. These are the cases where the two could drift.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSkippedOpsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val crop = FixtureTool("crop", ToolDomain.Geometry)
    private val adjust = FixtureTool("adjust", ToolDomain.Color)

    private fun doc(vararg ids: String) =
        EditDocument(ops = ids.map { ToolOp(it, it.let { _ -> crop.defaults() }) })

    @Test
    fun `an enabled tool's op is not skipped`() = onEditor(listOf(crop, adjust)) { harness ->
        val skipped = skippedOps(doc("crop", "adjust"), harness.registry, MediaKind.Image)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun `an op from an unregistered tool is skipped`() = onEditor(listOf(crop)) { harness ->
        val skipped = skippedOps(doc("crop", "ghost"), harness.registry, MediaKind.Image)
        assertEquals(listOf("ghost"), skipped.map { it.toolId })
    }

    @Test
    fun `an op from a registered but disabled tool is skipped`() =
        onEditor(listOf(crop, adjust)) { harness ->
            harness.settings.setEditorToolEnabled("adjust", enabled = false)

            val skipped = skippedOps(doc("crop", "adjust"), harness.registry, MediaKind.Image)
            assertEquals(listOf("adjust"), skipped.map { it.toolId })
        }

    @Test
    fun `document order is kept`() = onEditor(listOf(crop)) { harness ->
        val skipped = skippedOps(doc("a", "crop", "b"), harness.registry, MediaKind.Image)
        assertEquals(listOf("a", "b"), skipped.map { it.toolId })
    }

    @Test
    fun `the pending op is considered, like the fold considers it`() =
        onEditor(listOf(crop)) { harness ->
            val document = EditDocument(pending = ToolOp("ghost", crop.defaults()))
            assertEquals(1, skippedOps(document, harness.registry, MediaKind.Image).size)
        }

    @Test
    fun `titles name the known tools and stay silent about the unknown`() =
        onEditor(listOf(crop, adjust)) { harness ->
            harness.settings.setEditorToolEnabled("adjust", enabled = false)

            val skipped = skippedOps(doc("adjust", "ghost", "adjust"), harness.registry, MediaKind.Image)
            // Three ops skipped, one name to give — the count is what stays true.
            assertEquals(3, skipped.size)
            assertEquals(listOf("adjust"), skippedToolTitles(skipped, harness.registry))
        }
}
