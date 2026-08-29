package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
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
 * A caption's corners, driven by a pointer (ME-148).
 *
 * **Nothing in the pointer layer changed for this**, which is the claim worth driving
 * through the real overlay: `markupGestureAt` already prefers the selected shape's handles
 * over every shape's body, and `MarkupEdits.resize` already previews per event and commits
 * on release. A caption gaining handles is therefore a caption gaining a drag — and if it
 * is not, `frameHandles` is what is wrong rather than the recogniser.
 *
 * **The canvas scale is measured, never assumed.** The canvas is fitted to the window, so
 * a canvas pixel is not a source pixel (`MarkupMoveUiTest` measures a ratio for exactly
 * that reason). Here it comes from the caret's own field: that field is `wrap` source
 * pixels wide, so its measured width divided by `wrap` is the scale. The caption's `at`
 * is the click that placed it, and its top-left handle is the band below that (ME-152 —
 * the box starts at the ascender, not at the top of the font's line).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupTextFrameUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** Where `openCaret` places the caret — the caption's `at`, in canvas pixels. */
    private val placed = Offset(60f, 60f)

    private fun EditorHarness.caption(): MarkupShape.Text =
        assertIs<MarkupShape.Text>(
            session.document.value.markupShapes().single { it is MarkupShape.Text }
        )

    /**
     * Canvas pixels per source pixel, taken from the open caret's own field: it is laid
     * out at the caption's `wrap` source pixels, so its measured width divided by that is
     * the scale. Read while the caret is open, which is the only moment it is on screen.
     */
    private fun ComposeUiTest.measuredScale(harness: EditorHarness): Float {
        val field = onNodeWithTag(MARKUP_TEXT_FIELD_TAG).getBoundsInRoot()
        return (field.right - field.left).value / harness.caption().wrap
    }

    /** The caption's top-left handle, in canvas pixels, at the measured [scale]. */
    private fun cornerOf(caption: MarkupShape.Text, scale: Float): Offset {
        val band = requireNotNull(captionBand(caption))
        return Offset(placed.x, placed.y + band.start * scale)
    }

    @Test
    fun `dragging a corner grows the caption in one undo step`() {
        onEditor(listOf(MarkupTool)) { harness ->
            var before = 0f
            var ratio = 0f
            onUi {
                openCaret(harness)
                typeIntoCaret("Woka")
                val scale = measuredScale(harness)
                // Commits and leaves the caption selected, which is what puts its handles
                // on the canvas — `commitTextEdit` selects what it committed.
                finishCaret()
                before = harness.caption().width
                // `wrap / width` is what a scale leaves alone, which is why the ratio is
                // the assertion below rather than either number on its own.
                ratio = harness.caption().let { it.wrap / it.width }

                val corner = cornerOf(harness.caption(), scale)
                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(corner)
                    press()
                    // Away from the anchor, which for the top-left handle is the
                    // bottom-right one: the diagonal grows, so the type grows.
                    moveTo(corner + Offset(-20f, -20f))
                    moveTo(corner + Offset(-40f, -40f))
                    release()
                }
                waitForIdle()
            }

            val grown = harness.caption()
            assertTrue(grown.width > before, "the drag left the caption at ${grown.width}")
            // The box followed the type, which is the whole of what a corner does.
            assertEquals(ratio, grown.wrap / grown.width, absoluteTolerance = 0.001f)
            assertEquals(
                1,
                harness.session.document.value.opsOf(MarkupTool.manifest.id).size,
                "the drag added a second markup operation"
            )

            // Exactly one history entry, proven by taking it back.
            harness.undo()
            assertEquals(before, harness.caption().width)
        }
    }

    /** A press on a corner that never moves is a handle, not the click that reopens an edit. */
    @Test
    fun `a press on a corner opens no caret and writes nothing`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openCaret(harness)
                typeIntoCaret("Woka")
                val scale = measuredScale(harness)
                finishCaret()
                val settled = harness.caption()

                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(cornerOf(settled, scale))
                    press()
                    release()
                }
                waitForIdle()

                onNodeWithTag(MARKUP_TEXT_FIELD_TAG).assertDoesNotExist()
                assertEquals(settled, harness.caption(), "a press with no drag changed the caption")
            }
        }
    }
}
