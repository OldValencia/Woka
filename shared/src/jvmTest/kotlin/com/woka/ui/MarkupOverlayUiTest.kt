package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupShapes
import com.woka.support.MainDispatcherRule
import com.woka.support.flippedPreviewEngine
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drawing on the markup overlay (ME-114) — the second tool in the app whose gestures are
 * its own, and therefore the test of whether Crop's overlay pattern is a pattern.
 *
 * **One gesture is one undo step** (§3.1). The assertion that proves it is not the
 * operation count — `Single` cardinality keeps that at one however often the document was
 * committed — but the *undo*: one press must take the whole drag back. Moving a shape is
 * `MarkupMoveUiTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupOverlayUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a drag adds one shape and one undo step, mapped through the preview`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = flippedPreviewEngine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-markup").performClick()
                waitForIdle()
                assertTrue(harness.session.document.value.isEmpty, "opening the tool edited something")
                assertFalse(harness.session.canUndo.value, "opening the tool made an undo step")

                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(40f, 30f))
                    press()
                    moveTo(Offset(90f, 60f))
                    moveTo(Offset(140f, 100f))
                    release()
                }
                waitForIdle()

                val shapes = harness.session.document.value.markupShapes()
                assertEquals(1, shapes.size, "the drag drew something other than one shape")
                assertEquals(
                    1,
                    harness.session.document.value.opsOf(MarkupTool.manifest.id).size,
                    "the drag left more than one markup operation"
                )

                // Mirrored, so a rightward drag runs leftwards through the source — the
                // whole reason the fixture flips its preview.
                val arrow = assertIs<MarkupShape.Arrow>(shapes.single())
                assertTrue(arrow.from.x > arrow.to.x, "the drag was not mapped through the flip")

                harness.session.undo()
                waitForIdle()
                assertTrue(
                    harness.session.document.value.isEmpty,
                    "one undo did not take the whole gesture back — the overlay commits per event"
                )
            }
        }
    }

    /**
     * A press that never moves draws nothing: a stray click must not mint a zero-sized
     * shape, which is what makes the deselect-on-empty-picture rule safe.
     */
    @Test
    fun `a click on empty picture draws nothing at all`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = flippedPreviewEngine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-markup").performClick()
                waitForIdle()

                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(60f, 60f))
                    press()
                    release()
                }
                waitForIdle()

                assertTrue(harness.session.document.value.isEmpty, "a click drew a shape")
                assertNull(harness.session.document.value.pending, "a click left an operation in flight")
                assertFalse(harness.session.canUndo.value, "a click recorded an undo step")
            }
        }
    }
}
