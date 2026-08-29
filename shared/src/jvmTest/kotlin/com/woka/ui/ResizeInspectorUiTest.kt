package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.resize.ResizeTool
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The resize panel (ME-110): the footnote reads the picture's own numbers, and a chip
 * that means "this many pixels" resolves against the frame before anything is stored.
 *
 * **The frame reaches the row, never the tool's `stage()`.** That separation is the
 * whole reason the fold stays a pure function of parameters, and it is only observable
 * here — from a document, `1280` looks like any other stored choice.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ResizeInspectorUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(4000, 3000)

    private fun engine() = FixtureEngine(sourceSize = source)

    private fun openedAt(percent: Float) = EditDocument(
        ops = listOf(ToolOp(ResizeTool.manifest.id, ResizeTool.defaults().with(ResizeTool.Scale, percent)))
    )

    @Test
    fun `the footnote reads the source and the target`() {
        onEditor(listOf(ResizeTool), openedAt(50f)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = engine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-resize").performClick()
                waitForIdle()

                onNodeWithTag("editor-inspector-footnote")
                    .assertTextEquals("4000 × 3000 → 2000 × 1500")
            }
        }
    }

    /**
     * A reopened file shows what it carries, not the default — ME-134's contract, on the
     * first tool that has a value worth showing.
     */
    @Test
    fun `a reopened document shows its own scale`() {
        onEditor(listOf(ResizeTool), openedAt(25f)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = engine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-resize").performClick()
                waitForIdle()

                // By the readout's own tag: the `25%` preset chip carries the same text,
                // and what is being asserted is the *value*, not that the word appears.
                //
                // **Unmerged** since ME-155: the number sits inside the button that opens
                // the value's popover, and a clickable node merges its descendants — so
                // the tag exists only in the tree that has not been merged.
                onNodeWithTag("param-scale-readout", useUnmergedTree = true)
                    .assertTextEquals("25%")
            }
        }
    }

    /** `1280` on a 4000 px picture is 32 %, worked out at the click and stored as a number. */
    @Test
    fun `a long-edge chip resolves against the frame`() {
        onEditor(listOf(ResizeTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = engine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-resize").performClick()
                waitForIdle()

                onNodeWithText("1280").performClick()
                waitForIdle()

                val scale = harness.session.document.value.ops
                    .last { it.toolId == ResizeTool.manifest.id }.params[ResizeTool.Scale]
                assertEquals(32f, scale, 0.01f, "the chip did not resolve against the frame")
            }
        }
    }
}
