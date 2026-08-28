package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.filter.FilterTool
import com.woka.feature.editor.tools.filter.PhotoFilter
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What clicking an option in the preview strip does to the document (ME-112).
 *
 * The other half — what the strip *draws*, and how many times it asks for it — is
 * [PreviewChoiceRowUiTest]. Split because one file could not hold both and stay inside
 * the size mandate, and the seam is the natural one: a strip that renders correctly and
 * writes nothing is a different fault from one that writes correctly and shows the wrong
 * option as chosen.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class PreviewChoiceWriteUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun openedOn(filter: PhotoFilter) = EditDocument(
        ops = listOf(ToolOp(FilterTool.manifest.id, FilterTool.defaults().with(FilterTool.Filter, filter)))
    )

    @Test
    fun `choosing an option writes it and records one undo step`() {
        onEditor(listOf(FilterTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = FixtureEngine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-filter").performClick()
                waitForIdle()

                // Scrolled to first: since ME-141 the undertone and the look share
                // one line, and Noir is the seventh look — well past the right edge
                // of a 420 dp panel. A click on a node outside the viewport lands
                // on whatever is drawn there instead.
                onNodeWithTag("param-filter-${PhotoFilter.Noir.name}")
                    .performScrollTo()
                    .performClick()
                waitForIdle()

                val op = harness.session.document.value.ops
                    .last { it.toolId == FilterTool.manifest.id }
                assertEquals(PhotoFilter.Noir, op.params[FilterTool.Filter])
                assertTrue(harness.session.canUndo.value, "choosing a look recorded no undo step")

                harness.session.undo()
                waitForIdle()
                assertTrue(harness.session.document.value.isEmpty, "one undo did not take it back")
            }
        }
    }

    /** A reopened file shows what it carries — ME-134's contract, on a strip. */
    @Test
    fun `the selected option is the document's, not the default`() {
        onEditor(listOf(FilterTool), openedOn(PhotoFilter.Vivid)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = FixtureEngine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-filter").performClick()
                waitForIdle()

                assertEquals(
                    PhotoFilter.Vivid,
                    harness.session.document.value.ops
                        .last { it.toolId == FilterTool.manifest.id }.params[FilterTool.Filter],
                    "opening the tool reset the stored look"
                )
            }
        }
    }
}
