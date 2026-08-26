package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
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

    private fun ComposeUiTest.assertOnlyRow(shown: String, hidden: String) {
        onNodeWithTag("param-$shown").assertExists()
        onNodeWithTag("param-$hidden").assertDoesNotExist()
    }
}
