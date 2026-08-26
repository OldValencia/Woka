package com.woka.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.woka.feature.editor.tools.markup.MARKUP_TEXT_FIELD_TAG
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupShapes
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

/**
 * **`Enter` breaks a line; a click elsewhere commits** (asked for 2026-08-27).
 *
 * It shipped the other way with ME-145, when a caption was one unwrapped line and had
 * nowhere to break to. ME-146 gave it a box and a wrap width, so the key that ends a
 * paragraph has to end a paragraph — and the field has to stay open while it does.
 *
 * Its own file because `MarkupTextUiTest` is at the size mandate.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupTextEnterUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `Enter breaks the line instead of finishing the caption`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                val before = openCaret(harness)
                typeIntoCaret("Woka")

                onNodeWithTag(MARKUP_TEXT_FIELD_TAG).performKeyInput { pressKey(Key.Enter) }
                waitForIdle()

                onNodeWithTag(MARKUP_TEXT_FIELD_TAG).assertExists()
                assertEquals(before.ops, harness.session.document.value.ops, "Enter committed")

                typeIntoCaret("Two")
                finishCaret()

                val caption = assertIs<MarkupShape.Text>(
                    harness.session.document.value.markupShapes().single()
                )
                assertEquals("Woka\nTwo", caption.text, "Enter did not break the line")
            }
        }
    }
}
