package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.TextFace
import com.woka.feature.editor.tools.markup.markupShapes
import com.woka.support.EditorHarness
import com.woka.support.MainDispatcherRule
import com.woka.support.chooseMarkupPen
import com.woka.support.clickPicture
import com.woka.support.finishCaret
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openMarkupTool
import com.woka.support.toggleControl
import com.woka.support.typeIntoCaret
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * **Selecting a shape makes the panel describe that shape** (reported by hand 2026-08-29).
 *
 * `restyled` made a control *write* to the selection and nothing made it *read* from one,
 * so the bar went on reporting the pen: a yellow arrow reselected after the pen had been
 * set to blue still showed blue. That is not only a wrong reading — every control sends an
 * absolute value, so the next click writes from the number nobody was looking at, and
 * clicking the lit `Bold` chip of a caption whose face had been cleared turned it back on.
 *
 * These two drive the whole loop through the real panel, because the defect lives in the
 * join between the overlay's selection and the schema's rendering and in neither half
 * alone.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupSelectionReadbackUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /**
     * **Asserted on the readout, not on the document.** What went wrong is what the panel
     * *said*, and the document was right the whole time — a test reading the shape back
     * would have passed against the defect. `Width` is the spec whose reading is a string
     * on screen, so it is the one this is written against; a colour and a face set reach
     * the panel through the same line.
     */
    @Test
    fun `reselecting a shape shows the size it was drawn at, not the pen's`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                drawStroke()

                // Deselect, walk the pen up, then select the stroke drawn at 6 again.
                clickPicture(Offset(400f, 300f))
                repeat(4) {
                    onNodeWithTag("param-${MarkupTool.Width.key}-plus").performClick()
                    waitForIdle()
                }
                readout(MarkupTool.Width.key).assertTextEquals("10px")

                clickPicture(Offset(90f, 80f))

                readout(MarkupTool.Width.key).assertTextEquals("6px")
                assertEquals(
                    6f,
                    harness.session.document.value.markupShapes().single().width,
                    "the reselected stroke was not the one drawn at 6"
                )
            }
        }
    }

    /**
     * The face the defect was reported on: cleared while nothing was selected, then the
     * caption reselected — the chip has to come back lit, or pressing it turns the face
     * *on* when it was meant to turn it off.
     */
    @Test
    fun `a face cleared on the pen comes back lit when its caption is reselected`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                chooseMarkupPen("Text")
                clickPicture(Offset(60f, 60f))
                typeIntoCaret("Woka")

                toggleControl(MarkupTool.Faces.key)
                onNodeWithTag("param-faces-${TextFace.Bold.name}").performClick()
                waitForIdle()
                toggleControl(MarkupTool.Faces.key)
                finishCaret()

                // Nothing selected: this moves the pen alone.
                toggleControl(MarkupTool.Faces.key)
                onNodeWithTag("param-faces-${TextFace.Bold.name}").performClick()
                waitForIdle()
                toggleControl(MarkupTool.Faces.key)

                clickPicture(Offset(70f, 90f))
                // Pressing the chip now must *clear* the caption's face, which it can only
                // do if the panel was reporting the caption rather than the pen.
                toggleControl(MarkupTool.Faces.key)
                onNodeWithTag("param-faces-${TextFace.Bold.name}").performClick()
                waitForIdle()
            }

            assertEquals(emptySet(), harness.caption().faces)
        }
    }

    private fun EditorHarness.caption(): MarkupShape.Text =
        assertIs<MarkupShape.Text>(
            session.document.value.markupShapes().single { it is MarkupShape.Text }
        )

    /** The number the frame reports, which merges into a clickable node — hence unmerged. */
    private fun ComposeUiTest.readout(key: String) =
        onNodeWithTag("param-$key-readout", useUnmergedTree = true)

    private fun ComposeUiTest.drawStroke() {
        onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
            moveTo(Offset(40f, 40f))
            press()
            moveTo(Offset(150f, 120f))
            release()
        }
        waitForIdle()
    }
}
