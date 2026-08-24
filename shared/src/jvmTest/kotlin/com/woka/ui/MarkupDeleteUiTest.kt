package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.app.shell.GlobalShortcutState
import com.woka.feature.editor.shell.CanvasKeys
import com.woka.feature.editor.shell.EditorKeyEffect
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupShapes
import com.woka.support.EditorHarness
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
 * `Delete` reaching the open canvas tool through the editor's single keyboard slot
 * (ME-114).
 *
 * **The claim worth testing is the bridge**, not the arithmetic: the handler slot is
 * written by the mounted overlay and read by a key handler two composables above it, and
 * calling `MarkupTool.onDelete` directly would prove nothing about that wiring — which is
 * the half that can silently stop working.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupDeleteUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `Delete removes the selected shape as one undo step`() {
        onEditor(listOf(MarkupTool)) { harness ->
            val canvasKeys = CanvasKeys()
            onUi {
                openMarkup(harness, canvasKeys)

                // Drawn is selected: a shape is selected as it is drawn, so its handles
                // are there the moment it is released.
                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(40f, 40f))
                    press()
                    moveTo(Offset(150f, 120f))
                    release()
                }
                waitForIdle()
                assertEquals(1, harness.session.document.value.markupShapes().size)

                assertTrue(canvasKeys.onDelete?.invoke() == true, "Delete was not handled by the tool")
                waitForIdle()

                assertTrue(
                    harness.session.document.value.markupShapes().isEmpty(),
                    "Delete left the shape in the document"
                )
                harness.session.undo()
                waitForIdle()
                assertEquals(
                    1,
                    harness.session.document.value.markupShapes().size,
                    "one undo did not bring the deleted shape back"
                )
            }
        }
    }

    /**
     * Nothing selected: `onDelete` answers false — "handled, but there was nothing to do".
     * The key is consumed either way, so it never falls through to the viewer beneath.
     */
    @Test
    fun `Delete with nothing selected changes the document not at all`() {
        onEditor(listOf(MarkupTool)) { harness ->
            val canvasKeys = CanvasKeys()
            onUi {
                openMarkup(harness, canvasKeys)
                val before = harness.session.document.value

                assertEquals(false, canvasKeys.onDelete?.invoke(), "an empty selection reported a deletion")
                waitForIdle()
                assertEquals(before, harness.session.document.value, "Delete edited an empty selection")
            }
        }
    }

    /** The slot is given back on dispose: a handler left behind deletes out of a closed tool. */
    @Test
    fun `closing the tool clears the delete handler`() {
        onEditor(listOf(MarkupTool)) { harness ->
            val canvasKeys = CanvasKeys()
            onUi {
                openMarkup(harness, canvasKeys)
                assertTrue(canvasKeys.onDelete != null, "an open canvas tool registered no handler")

                // The rail toggles: a second click on the active tool closes it.
                onNodeWithTag("editor-tool-markup").performClick()
                waitForIdle()
                assertTrue(canvasKeys.onDelete == null, "the handler outlived the tool that owned it")
            }
        }
    }
}

/**
 * The editor with Markup open — the setup every test above shares verbatim.
 *
 * Local to this file rather than in `support/`: a harness there is justified once two test
 * *classes* need it, and only this one does (`code-style/test.md`).
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.openMarkup(harness: EditorHarness, canvasKeys: CanvasKeys) {
    setContent {
        // The key handler is mounted too, as `EditorHost` always mounts it: the claim
        // under test is the slot travelling from the overlay up to this effect, and a
        // screen composed without it would assert only half of that.
        EditorKeyEffect(
            globalShortcuts = GlobalShortcutState(),
            canUndo = false,
            canRedo = false,
            onCancel = {},
            onUndo = {},
            onRedo = {},
            onDone = {},
            canvasKeys = canvasKeys
        )
        EditorScreen(
            session = harness.session,
            engine = FixtureEngine(),
            registry = harness.registry,
            onCancel = {},
            onDone = {},
            canvasKeys = canvasKeys
        )
    }
    onNodeWithTag("editor-tool-markup").performClick()
    waitForIdle()
}
