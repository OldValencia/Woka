package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.shell.CanvasKeys
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MARKUP_TEXT_FIELD_TAG
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupShapes
import com.woka.support.MainDispatcherRule
import com.woka.support.chooseMarkupPen
import com.woka.support.clickPicture
import com.woka.support.closeMarkupTool
import com.woka.support.drawArrow
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A mark touched with **no tool open at all** (ME-149).
 *
 * The claim is the shell's, not the tool's: with nothing open the editor mounts the tool
 * that owns a mark anyway, in select-only mode. So these drive the real screen — a test
 * that composed `MarkupOverlay` itself would assert the overlay and skip the mounting,
 * which is the whole of what the ticket adds.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupIdleUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** The shaft's midpoint of the arrow [drawArrow] leaves — its body, not a handle. */
    private val middle = Offset(95f, 80f)

    @Test
    fun `a click selects a mark with no tool open, and Delete removes it`() {
        onEditor(listOf(MarkupTool)) { harness ->
            val canvasKeys = CanvasKeys()
            onUi {
                drawArrow(harness, canvasKeys)
                closeMarkupTool()

                onNodeWithTag("editor-inspector").assertDoesNotExist()
                onNodeWithTag(MARKUP_OVERLAY_TAG).assertExists()

                // Deselected first: a shape is selected as it is drawn, so without this
                // the `Delete` below would prove nothing about the click that follows.
                clickPicture(Offset(400f, 300f))
                clickPicture(middle)

                assertTrue(canvasKeys.onDelete?.invoke() == true, "the click selected nothing")
                waitForIdle()
                assertTrue(
                    harness.session.document.value.markupShapes().isEmpty(),
                    "Delete left the shape in the document"
                )
                // Nothing left to own, so nothing is mounted and the picture is bare.
                onNodeWithTag(MARKUP_OVERLAY_TAG).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `a drag moves a mark with no tool open, in one undo step`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                val drawn = drawArrow(harness, CanvasKeys())
                closeMarkupTool()

                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(middle)
                    press()
                    moveTo(middle + Offset(30f, 20f))
                    moveTo(middle + Offset(60f, 40f))
                    release()
                }
                waitForIdle()

                val moved = assertIs<MarkupShape.Arrow>(
                    harness.session.document.value.markupShapes().single()
                )
                assertEquals(drawn.id, moved.id, "the drag drew a new shape instead of moving one")
                assertTrue(moved.from.x > drawn.from.x, "the shape did not move")
                assertEquals(
                    1,
                    harness.session.document.value.opsOf(MarkupTool.manifest.id).size,
                    "the move added a second markup operation"
                )

                // Exactly one entry, proven by taking it back.
                harness.session.undo()
                waitForIdle()
                assertEquals(drawn, harness.session.document.value.markupShapes().single())
            }
        }
    }

    /** The pen is off: neither a drag nor a click with the text pen last out creates. */
    @Test
    fun `nothing is created with no tool open`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                drawArrow(harness, CanvasKeys())
                chooseMarkupPen("Text")
                closeMarkupTool()
                val before = harness.session.document.value

                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(300f, 240f))
                    press()
                    moveTo(Offset(380f, 300f))
                    release()
                }
                waitForIdle()
                clickPicture(Offset(300f, 240f))

                assertEquals(before, harness.session.document.value, "the idle overlay drew")
                onNodeWithTag(MARKUP_TEXT_FIELD_TAG).assertDoesNotExist()
            }
        }
    }
}
