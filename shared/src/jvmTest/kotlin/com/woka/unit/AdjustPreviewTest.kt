package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.commit
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.shell.ParamWriter
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * What a drag looks like while it is happening (2026-08-22), reported by hand as two
 * separate complaints that turned out to be two separate bugs.
 *
 * **A preview must render what committing produces** (§3.1). It did not: the fold added
 * `pending` to `ops`, so a `Single` tool with an operation already in the document
 * applied *both* — dragging Tint from −100 to +100 looked roughly neutral and snapped to
 * green on release.
 *
 * **And a drag must build on what the document holds now.** `DetentSlider` keeps its
 * gesture lambda in a `pointerInput`, so a `ParamWriter` holding a captured document
 * wrote against a stale one and wiped whichever slider had been moved before it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjustPreviewTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(640, 480)

    private fun op(vararg values: Pair<FloatParam, Float>): ToolOp {
        var params = AdjustTool.defaults()
        values.forEach { (spec, amount) -> params = params.with(spec, amount) }
        return ToolOp(AdjustTool.manifest.id, params)
    }

    /** The bug, in one assertion: in flight must equal committed. */
    @Test
    fun `a pending adjustment replaces the committed one rather than stacking on it`() {
        withFoldRegistry(listOf(AdjustTool)) { registry, _ ->
            val dragging = EditDocument(
                ops = listOf(op(AdjustTool.Tint to -100f)),
                pending = op(AdjustTool.Tint to 100f)
            )
            val committed = EditDocument(ops = listOf(op(AdjustTool.Tint to 100f)))

            val previewed = fold(dragging, registry, source).colorFilter
            val settled = fold(committed, registry, source).colorFilter

            assertEquals(settled, previewed, "the preview showed something other than the result")
        }
    }

    /** A crop's rectangle had the same disease: two crops, one of them in flight. */
    @Test
    fun `a pending geometry stage replaces the committed one too`() {
        withFoldRegistry(listOf(AdjustTool)) { registry, _ ->
            val dragging = EditDocument(
                ops = listOf(op(AdjustTool.Exposure to 60f)),
                pending = op(AdjustTool.Exposure to 60f)
            )
            val once = EditDocument(ops = listOf(op(AdjustTool.Exposure to 60f)))

            assertContentEquals(
                fold(once, registry, source).matrix.values,
                fold(dragging, registry, source).matrix.values,
                "the same operation was applied twice"
            )
        }
    }

    /**
     * The second bug, driven the way the slider drives it: a writer built against an
     * **older** document must still extend what the session holds now.
     */
    @Test
    fun `a stale writer still builds on the current document`() {
        onEditor(listOf(AdjustTool)) { harness ->
            // The writer a gesture captured before anything had been committed.
            val stale = ParamWriter(harness.session, AdjustTool)

            stale.edit(AdjustTool.Contrast, 40f)
            harness.session.commit(AdjustTool.manifest.cardinality)

            // The same writer, now moving a different slider.
            stale.edit(AdjustTool.Saturation, -30f)
            harness.session.commit(AdjustTool.manifest.cardinality)

            val params = harness.session.document.value.ops
                .last { it.toolId == AdjustTool.manifest.id }.params
            assertEquals(40f, params[AdjustTool.Contrast], "the earlier slider was wiped")
            assertEquals(-30f, params[AdjustTool.Saturation])
        }
    }
}
