package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.crop.CROP_OVERLAY_TAG
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The crop overlay driven by a pointer (ME-109) — the first tool in the app whose
 * gestures are its own.
 *
 * **One gesture is one undo step** (§3.1). The assertion that proves it is not the
 * operation count — `Single` cardinality would keep that at one however many times the
 * document was committed — but the *undo*: one press must take the whole drag back to
 * an untouched document. A commit per pointer event would leave it somewhere in the
 * middle of the drag instead.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CropOverlayUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a drag on the overlay records exactly one undo step`() {
        onEditor(listOf(CropTool)) { harness ->
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
                onNodeWithTag("editor-tool-crop").performClick()
                waitForIdle()

                assertTrue(harness.session.document.value.isEmpty, "opening the tool edited something")
                assertFalse(harness.session.canUndo.value, "opening the tool made an undo step")

                // From the top-left corner handle, inwards.
                onNodeWithTag(CROP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(2f, 2f))
                    press()
                    moveTo(Offset(40f, 40f))
                    moveTo(Offset(90f, 70f))
                    release()
                }
                waitForIdle()

                val document = harness.session.document.value
                assertEquals(1, document.opsOf(CropTool.manifest.id).size, "the drag left more than one crop")
                assertTrue(harness.session.canUndo.value, "the drag recorded no undo step at all")

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
     * A press in the part being discarded grabs nothing and must not start an edit.
     *
     * It needs a crop already in place: an untouched rect *is* the whole frame, so
     * every point inside the overlay is either a handle or its body, and a press
     * outside the overlay is one Compose refuses to inject at all.
     *
     * Asserted on the **document**, not on `canUndo`: a session opened on a file that
     * already carries operations starts with a history reconstructed from them
     * (`EditorHistory.startingAt`, ME-134), so Undo is live before anything is done.
     */
    @Test
    fun `a drag outside the rect changes nothing`() {
        val corner = CropRect(0, 0, 100, 100)
        val initial = EditDocument(
            // The tool's full parameter set, as the overlay writes it: a partial one
            // is completed by the inspector on open, and that is an undo step.
            ops = listOf(ToolOp(CropTool.manifest.id, CropTool.defaults().with(CropTool.Rect, corner)))
        )
        onEditor(listOf(CropTool), initial) { harness ->
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
                onNodeWithTag("editor-tool-crop").performClick()
                waitForIdle()
                val before = harness.session.document.value

                onNodeWithTag(CROP_OVERLAY_TAG).performMouseInput {
                    moveTo(center)
                    press()
                    moveTo(center + Offset(60f, 60f))
                    release()
                }
                waitForIdle()

                assertEquals(corner, harness.session.document.value.ops.last().params[CropTool.Rect])
                assertEquals(before, harness.session.document.value, "an idle drag edited the document")
                assertNull(harness.session.document.value.pending, "an idle drag left an operation in flight")
            }
        }
    }
}
