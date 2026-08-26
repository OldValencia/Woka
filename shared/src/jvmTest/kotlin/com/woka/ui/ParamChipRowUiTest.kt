package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.tools.crop.CropAspect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.markup.MarkupKind
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupKind
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.support.EditorUnderShell
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openMarkupTool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Chips drawn as glyphs, and the ones that are not (ME-144, asked for 2026-08-27).
 *
 * **A glyph must not cost an option its name.** Markup's five shapes spelled out wrapped
 * the row onto two lines and reflowed the inspector as the pen was changed, so the words
 * moved into a tooltip — but the option is still called what it was called, and that has
 * to be assertable. `contentDescription` is where the name lands for anything that reads
 * the screen aloud, which is also what makes it the honest thing to test against.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ParamChipRowUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a chip with a glyph keeps its name and its click`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)

                MarkupKind.entries.forEach { kind ->
                    onNodeWithContentDescription(kind.name).assertExists()
                    // And the word itself is gone from the row it used to wrap.
                    onNodeWithText(kind.name).assertDoesNotExist()
                }

                onNodeWithTag("param-kind-${MarkupKind.Freehand.name}").performClick()
                waitForIdle()
                assertEquals(MarkupKind.Freehand, harness.session.document.value.markupKind())
            }
        }
    }

    /**
     * The default is still words. An icon earns its place where the words are longer than
     * the thing they name; `4:3` is already shorter than any glyph for it would be.
     */
    @Test
    fun `a chip row without glyphs is still spelled out`() {
        onEditor(listOf(CropTool)) { harness ->
            onUi {
                setContent { EditorUnderShell(harness, TopBarAccessoriesState()) }
                onNodeWithTag("editor-tool-crop").performClick()
                waitForIdle()

                CropAspect.entries.forEach { aspect ->
                    onNodeWithText(aspect.label).assertExists()
                }
                onNodeWithContentDescription(CropAspect.Square.label).assertDoesNotExist()
            }
        }
    }
}
