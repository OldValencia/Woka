package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MARKUP_TEXT_FIELD_TAG
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.captionBand
import com.woka.feature.editor.tools.markup.markupShapes
import com.woka.support.EditorHarness
import com.woka.support.MainDispatcherRule
import com.woka.support.closeMarkupTool
import com.woka.support.finishCaret
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openCaret
import com.woka.support.typeIntoCaret
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Resizing with no tool open (ME-149) — ME-148's corner handles, reached from the idle
 * mounting.
 *
 * Split from `MarkupIdleUiTest` for the size mandate, and it earns its own file anyway:
 * a caption is the shape whose handles came last, and the geometry it needs is
 * `MarkupTextFrameUiTest`'s — the caret's own field measures the canvas scale, and the
 * top-left handle is the band below where the caret was placed (ME-152).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupIdleCaptionUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** Where `openCaret` places the caret — the caption's `at`, in canvas pixels. */
    private val placed = Offset(60f, 60f)

    @Test
    fun `a caption's corner resizes it with no tool open`() {
        onEditor(listOf(MarkupTool)) { harness ->
            var before = 0f
            onUi {
                openCaret(harness)
                typeIntoCaret("Woka")

                // Read while the caret is open, the only moment it is on screen: the
                // field is laid out at the caption's `wrap` source pixels, so its
                // measured width divided by that is canvas pixels per source pixel.
                val field = onNodeWithTag(MARKUP_TEXT_FIELD_TAG).getBoundsInRoot()
                val scale = (field.right - field.left).value / harness.caption().wrap

                // Commits and leaves the caption selected, which is what carries the
                // handles across the close: the selection outlives the open tool.
                finishCaret()
                closeMarkupTool()
                before = harness.caption().width

                onNodeWithTag("editor-inspector").assertDoesNotExist()
                val band = requireNotNull(captionBand(harness.caption()))
                val corner = Offset(placed.x, placed.y + band.start * scale)
                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(corner)
                    press()
                    // Away from the anchor — the bottom-right corner — so the type grows.
                    moveTo(corner + Offset(-20f, -20f))
                    moveTo(corner + Offset(-40f, -40f))
                    release()
                }
                waitForIdle()
            }

            val grown = harness.caption()
            assertTrue(grown.width > before, "the drag left the caption at ${grown.width}")
            assertEquals(
                1,
                harness.session.document.value.opsOf(MarkupTool.manifest.id).size,
                "the drag added a second markup operation"
            )

            harness.undo()
            assertEquals(before, harness.caption().width, "one undo did not take the resize back")
        }
    }
}

private fun EditorHarness.caption(): MarkupShape.Text =
    assertIs<MarkupShape.Text>(
        session.document.value.markupShapes().single { it is MarkupShape.Text }
    )
