package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.shell.paramsOf
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.support.EditorHarness
import com.woka.support.MainDispatcherRule
import com.woka.support.chooseMarkupPen
import com.woka.support.clickPicture
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openMarkupTool
import com.woka.support.typeIntoCaret
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Dragging** a size row, as opposed to tapping it (reported by hand 2026-08-26).
 *
 * `DetentSlider` answers a tap and a horizontal drag through two separate `pointerInput`
 * blocks, and every editor test so far has used the tap — `performClick`. A knob that will
 * not follow the pointer is therefore a failure every green test in this repository is
 * blind to, which is the whole reason this file exists.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupSliderDragUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `dragging the width row moves the pen`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                val before = penValue(harness, MarkupTool.Width)

                dragRow(MarkupTool.Width.key)

                assertTrue(
                    penValue(harness, MarkupTool.Width) != before,
                    "the width knob did not follow the pointer; still $before"
                )
            }
        }
    }

    /** The same drag, on the row a caption is sized by, with a caret open behind it. */
    @Test
    fun `dragging the text size row moves the pen with a caret open`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                chooseMarkupPen("Text")
                clickPicture(Offset(60f, 60f))
                typeIntoCaret("Woka")
                val before = penValue(harness, MarkupTool.TextSize)

                dragRow(MarkupTool.TextSize.key)

                assertTrue(
                    penValue(harness, MarkupTool.TextSize) != before,
                    "the text size knob did not follow the pointer; still $before"
                )
            }
        }
    }

    /**
     * **The row a pen swap put in place of another one** (found by hand 2026-08-26).
     *
     * `MarkupTool.rows` drops `Width` and adds `Text size` when the text pen is chosen, and
     * the two land in the same position of the same `Column`. Without an identity of its
     * own the row is the *same* composition node before and after: the label and the value
     * follow the new spec, but `DetentSlider` keeps its gesture in a `pointerInput` whose
     * keys did not change, and that gesture still closes over the spec it was built with.
     * So the row reads "Text size" and writes `width`.
     */
    @Test
    fun `the size row a pen swap put in place writes its own spec`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                val widthBefore = penValue(harness, MarkupTool.Width)
                chooseMarkupPen("Text")
                clickPicture(Offset(60f, 60f))
                typeIntoCaret("Woka")

                dragRow(MarkupTool.TextSize.key)

                assertEquals(
                    widthBefore,
                    penValue(harness, MarkupTool.Width),
                    "the text size row wrote into the stroke width it replaced"
                )
                assertTrue(
                    penValue(harness, MarkupTool.TextSize) != 64f,
                    "the text size row moved nothing"
                )
            }
        }
    }

    private fun penValue(harness: EditorHarness, spec: FloatParam) =
        paramsOf(harness.session.document.value, MarkupTool)[spec]

    /** Press at the left of the track and pull to the right, past the drag slop. */
    private fun ComposeUiTest.dragRow(key: String) {
        onNodeWithTag("param-$key").performMouseInput {
            moveTo(Offset(4f, centerY))
            press()
            moveTo(Offset(width * 0.4f, centerY))
            moveTo(Offset(width * 0.8f, centerY))
            release()
        }
        waitForIdle()
    }
}
