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
import kotlin.test.assertIs

/**
 * Moving a drawn shape (ME-114) — split from `MarkupOverlayUiTest` for the size mandate.
 *
 * **This is the regression the ticket asks for**, and it took two attempts to make it
 * one. With the scope captured directly instead of through `rememberUpdatedState`, every
 * event of a drag maps from the document as it was at the press, so the shape ends up
 * displaced by the *last* event's delta alone. Asserting merely that it moved — or that it
 * kept its size — passes with that bug present, which was verified by re-introducing it
 * (2026-08-24). Only the assertion on the **total** displacement fails.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupMoveUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a drag over an existing shape moves it by the whole drag, not its last event`() {
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
                    moveTo(Offset(40f, 40f))
                    press()
                    moveTo(Offset(150f, 120f))
                    release()
                }
                waitForIdle()
                val drawn = assertIs<MarkupShape.Arrow>(
                    harness.session.document.value.markupShapes().single()
                )

                // On the shaft's midpoint, so the press lands on the stroke and not on
                // either endpoint's handle — a handle would resize instead of move.
                val middle = Offset(95f, 80f)
                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(middle)
                    press()
                    moveTo(middle + Offset(30f, 20f))
                    moveTo(middle + Offset(60f, 40f))
                    release()
                }
                waitForIdle()

                val shapes = harness.session.document.value.markupShapes()
                val moved = assertIs<MarkupShape.Arrow>(shapes.single())
                assertEquals(drawn.id, moved.id, "the second drag drew a new shape instead of moving one")
                assertEquals(
                    1,
                    harness.session.document.value.opsOf(MarkupTool.manifest.id).size,
                    "moving a shape added a second markup operation"
                )

                // **Expressed against the first drag, never in raw pixels.** The canvas
                // is fitted to the window, so a canvas pixel is not a source pixel and
                // the ratio is not known here. The first drag spanned 110 canvas px and
                // produced `drawn`; this one spanned 60, so the displacement must be
                // 60/110 of that span — which is the assertion that bites, because one
                // event's delta alone is 30/110 of it.
                val perCanvasPx = (drawn.to.x - drawn.from.x) / 110f
                assertEquals(
                    drawn.from.x + perCanvasPx * 60f,
                    moved.from.x,
                    absoluteTolerance = 1.5f,
                    message = "the shape moved by one event's delta, not the drag's"
                )
                // Moved, not reshaped: both ends travelled together.
                assertEquals(
                    drawn.to.x - drawn.from.x,
                    moved.to.x - moved.from.x,
                    absoluteTolerance = 0.5f,
                    message = "the move resized the shape"
                )
            }
        }
    }
}
