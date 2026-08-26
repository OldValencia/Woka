package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.tools.markup.MARKUP_TEXT_FIELD_TAG
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupShapes
import com.woka.support.EditorHarness
import com.woka.support.MainDispatcherRule
import com.woka.support.chooseMarkupPen
import com.woka.support.clickPicture
import com.woka.support.finishCaret
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openMarkupTool
import com.woka.support.typeIntoCaret
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Typing a caption onto the picture (ME-145).
 *
 * **The whole flow is arranged around an empty caption not being work.** A click places a
 * caret and previews a blank caption — in flight, never committed — so the style rows can
 * reach it and the canvas can draw what is typed. Nothing is *committed* until a string
 * exists, which is what keeps a stray click from leaving an invisible mark in the user's
 * file: the failure ME-122's `stage()` filter guards from the other side.
 *
 * `ops` is therefore what these assert on rather than `canUndo` — choosing the text pen is
 * itself an operation, so the editor has something to undo before a caret is ever placed.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupTextUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a click with the text pen opens a caret and commits nothing`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                val before = openCaret(harness)

                onNodeWithTag(MARKUP_TEXT_FIELD_TAG).assertExists()
                assertEquals(before.ops, harness.session.document.value.ops, "a caret committed")
                assertTrue(
                    harness.session.document.value.markupShapes()
                        .filterIsInstance<MarkupShape.Text>().all { it.text.isBlank() },
                    "a caret drew something"
                )
            }
        }
    }

    @Test
    fun `typing shows on the canvas and Enter commits one undo step`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                val before = openCaret(harness)
                typeIntoCaret("Woka")

                // In flight from the first keystroke — which is what puts it on the canvas
                // and in reach of the size and colour rows — and committed by none of them.
                val typed = harness.session.document.value.markupShapes()
                assertTrue(typed.any { it is MarkupShape.Text && it.text == "Woka" })
                assertEquals(before.ops, harness.session.document.value.ops, "a keystroke committed")

                finishCaret()
                val caption = assertIs<MarkupShape.Text>(
                    harness.session.document.value.markupShapes().single()
                )
                assertEquals("Woka", caption.text)
                onNodeWithTag(MARKUP_TEXT_FIELD_TAG).assertDoesNotExist()

                harness.session.undo()
                waitForIdle()
                assertTrue(
                    harness.session.document.value.markupShapes().isEmpty(),
                    "one undo did not take the whole caption back"
                )
            }
        }
    }

    /** The "discarded when empty" rule: a caret opened and closed costs the file nothing. */
    @Test
    fun `a caret with nothing typed into it leaves the document untouched`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                val before = openCaret(harness)
                finishCaret()

                onNodeWithTag(MARKUP_TEXT_FIELD_TAG).assertDoesNotExist()
                assertEquals(before, harness.session.document.value, "an empty caret edited it")
            }
        }
    }

    /** `Esc` abandons the caption rather than committing it — and leaves no undo step. */
    @Test
    fun `Esc abandons what was typed`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                val before = openCaret(harness)
                typeIntoCaret("Woka")

                onNodeWithTag(MARKUP_TEXT_FIELD_TAG).performKeyInput { pressKey(Key.Escape) }
                waitForIdle()

                onNodeWithTag(MARKUP_TEXT_FIELD_TAG).assertDoesNotExist()
                assertEquals(before, harness.session.document.value, "Esc committed the caption")
            }
        }
    }

    /**
     * Open the tool, choose the text pen, click the picture once — and answer the document
     * as it stood **before** the caret, which is what an abandoned edit has to restore.
     */
    private fun ComposeUiTest.openCaret(harness: EditorHarness): EditDocument {
        openMarkupTool(harness)
        chooseMarkupPen("Text")
        val before = harness.session.document.value
        clickPicture(Offset(60f, 60f))
        return before
    }
}
