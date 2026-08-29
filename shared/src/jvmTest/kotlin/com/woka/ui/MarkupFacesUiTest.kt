package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import com.woka.support.openCaret
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
 * The `Style` row driving a real caption (ME-151), through both paths it has.
 *
 * **The two are genuinely different code and both have failed before.** A face chosen
 * *before* typing has to reach the shape through the factory (`newMarkupShape`); one chosen
 * *while* a caption is selected has to reach it through `restyleSelected`. ME-146 shipped
 * with the second working and the first ignored for the plate, which is exactly the shape
 * of defect these two tests separate.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupFacesUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun chipOf(face: TextFace) = "param-${MarkupTool.Faces.key}-${face.name}"

    private fun EditorHarness.caption(): MarkupShape.Text =
        assertIs<MarkupShape.Text>(
            session.document.value.markupShapes().single { it is MarkupShape.Text }
        )

    @Test
    fun `a face chosen before any caret is carried by the caption then minted`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                chooseMarkupPen("Text")
                // **Nothing is selected here**, so this can only move the pen — which
                // makes the caption below the factory's work and nothing else's.
                toggleControl(MarkupTool.Faces.key)
                onNodeWithTag(chipOf(TextFace.Bold)).performClick()
                waitForIdle()
                toggleControl(MarkupTool.Faces.key)

                clickPicture(Offset(60f, 60f))
                typeIntoCaret("Woka")
                finishCaret()
            }

            assertEquals(setOf(TextFace.Bold), harness.caption().faces)
        }
    }

    @Test
    fun `a second face joins the first on a caption already typed`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openCaret(harness)
                typeIntoCaret("Woka")
                toggleControl(MarkupTool.Faces.key)
                onNodeWithTag(chipOf(TextFace.Bold)).performClick()
                waitForIdle()
                onNodeWithTag(chipOf(TextFace.Italic)).performClick()
                waitForIdle()
                toggleControl(MarkupTool.Faces.key)
                finishCaret()
            }

            assertEquals(setOf(TextFace.Bold, TextFace.Italic), harness.caption().faces)
        }
    }

    /** And a lit chip pressed again takes that face off, leaving the other where it is. */
    @Test
    fun `pressing a lit chip again clears that face alone`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openCaret(harness)
                typeIntoCaret("Woka")
                toggleControl(MarkupTool.Faces.key)
                onNodeWithTag(chipOf(TextFace.Underline)).performClick()
                waitForIdle()
                onNodeWithTag(chipOf(TextFace.Strikethrough)).performClick()
                waitForIdle()
                onNodeWithTag(chipOf(TextFace.Underline)).performClick()
                waitForIdle()
                toggleControl(MarkupTool.Faces.key)
                finishCaret()
            }

            assertEquals(setOf(TextFace.Strikethrough), harness.caption().faces)
        }
    }
}
