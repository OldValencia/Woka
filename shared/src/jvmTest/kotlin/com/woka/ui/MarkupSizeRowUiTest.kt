package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupShapes
import com.woka.support.MainDispatcherRule
import com.woka.support.chooseMarkupPen
import com.woka.support.clickPicture
import com.woka.support.finishCaret
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openMarkupTool
import com.woka.support.tapSizeRow
import com.woka.support.typeIntoCaret
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The size rows, and the selection they have to reach (reported by hand 2026-08-26).
 *
 * **The last test is the one that found the cause.** Clicking a row is what blurs an open
 * field, so the row's write runs *before* the edit is committed — and a caption that only
 * entered the document on commit was, at that moment, neither in the list nor selected.
 * `restyleSelected` looks a shape up by id, found nothing, and the row silently moved the
 * pen instead. The fix is not in the row: a caption is now previewed into the document the
 * moment its caret is placed (`MarkupTextEdits`), which is also what puts it on the canvas
 * as it is typed.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupSizeRowUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `the width row resizes a stroke selected after the slider was composed`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(40f, 40f))
                    press()
                    moveTo(Offset(160f, 130f))
                    release()
                }
                waitForIdle()
                val before = harness.session.document.value.markupShapes().single().width

                tapSizeRow(MarkupTool.Width.key)

                val after = harness.session.document.value.markupShapes().single().width
                assertTrue(after != before, "the slider moved the pen and left the shape at $before")
            }
        }
    }

    /** Selected by **clicking it**, which is what the report described. */
    @Test
    fun `the text size row resizes a caption selected by clicking it`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                chooseMarkupPen("Text")
                clickPicture(Offset(60f, 60f))
                typeIntoCaret("Woka")
                finishCaret()

                // Deselect, then select it again the way a user does.
                clickPicture(Offset(400f, 300f))
                clickPicture(Offset(70f, 90f))

                val before = harness.session.document.value.markupShapes().single().width
                tapSizeRow(MarkupTool.TextSize.key)

                val after = harness.session.document.value.markupShapes().single().width
                assertTrue(after != before, "the clicked caption stayed at $before")
            }
        }
    }

    /**
     * **Typed, then the slider — without `Enter` first.** Clicking the slider is what blurs
     * the field, so the caption is committed by that very click, and the write that follows
     * has to find it already in the list.
     */
    @Test
    fun `the text size row resizes a caption the same click committed`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                chooseMarkupPen("Text")
                clickPicture(Offset(60f, 60f))
                typeIntoCaret("Woka")

                tapSizeRow(MarkupTool.TextSize.key)

                val shapes = harness.session.document.value.markupShapes()
                assertEquals(1, shapes.size, "the caption was lost when the field blurred")
                assertTrue(shapes.single().width != 64f, "the caption stayed at its default size")
            }
        }
    }
}
