package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.support.MainDispatcherRule
import com.woka.support.chooseMarkupPen
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openMarkupTool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test

/**
 * **One size row at a time** (asked for 2026-08-26).
 *
 * A stroke width does nothing to a caption and a font size does nothing to an arrow, and a
 * row that changes nothing when it is dragged is worse than a missing one — it reads as
 * broken. `EditorTool.rows` is the hook: the schema is still the UI, but which of a tool's
 * parameters currently apply is a question only that tool can answer.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupRowVisibilityUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `the pen decides which size row is drawn`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                assertOnlyRow(MarkupTool.Width.key, hidden = MarkupTool.TextSize.key)

                chooseMarkupPen("Text")
                assertOnlyRow(MarkupTool.TextSize.key, hidden = MarkupTool.Width.key)

                // And back, because a pen chosen twice must not leave two rows behind.
                chooseMarkupPen("Arrow")
                assertOnlyRow(MarkupTool.Width.key, hidden = MarkupTool.TextSize.key)
            }
        }
    }

    /**
     * The plate row is the same rule with only one side (ME-146): a background does nothing
     * to an arrow, an ellipse, a rectangle or a freehand stroke, so it is drawn for the text
     * pen and for nothing else.
     */
    @Test
    fun `the plate row is drawn for the text pen alone`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                onNodeWithTag("param-${MarkupTool.Fill.key}").assertDoesNotExist()

                chooseMarkupPen("Text")
                onNodeWithTag("param-${MarkupTool.Fill.key}").assertExists()

                chooseMarkupPen("Freehand")
                onNodeWithTag("param-${MarkupTool.Fill.key}").assertDoesNotExist()
            }
        }
    }

    /**
     * **`Corner` needs a plate, not merely a caption.** With no fill chosen it rounds
     * nothing that can be seen, which is the same "a row that changes nothing reads as
     * broken" rule one level down — keyed on the value in the row above rather than on the
     * pen (asked for 2026-08-27).
     */
    @Test
    fun `the corner row appears only once a plate is chosen`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                chooseMarkupPen("Text")
                onNodeWithTag("param-${MarkupTool.Corner.key}").assertDoesNotExist()

                // The second swatch: the first is the "none" the row opens on.
                onNodeWithTag("param-${MarkupTool.Fill.key}-swatch-1").performClick()
                waitForIdle()

                onNodeWithTag("param-${MarkupTool.Corner.key}").assertExists()
            }
        }
    }

    /**
     * The faces row follows the plate row exactly (ME-151): a weight, a slant and a line
     * are all things only type has, so the four stroked pens must not offer them.
     */
    @Test
    fun `the faces row is drawn for the text pen alone`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                onNodeWithTag("param-${MarkupTool.Faces.key}").assertDoesNotExist()

                chooseMarkupPen("Text")
                onNodeWithTag("param-${MarkupTool.Faces.key}").assertExists()

                chooseMarkupPen("Rectangle")
                onNodeWithTag("param-${MarkupTool.Faces.key}").assertDoesNotExist()
            }
        }
    }

    private fun ComposeUiTest.assertOnlyRow(shown: String, hidden: String) {
        onNodeWithTag("param-$shown").assertExists()
        onNodeWithTag("param-$hidden").assertDoesNotExist()
    }
}
