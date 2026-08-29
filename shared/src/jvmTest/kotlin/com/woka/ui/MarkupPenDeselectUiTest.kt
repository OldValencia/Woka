package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupShapes
import com.woka.support.MainDispatcherRule
import com.woka.support.chooseMarkupPen
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openMarkupTool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Choosing a pen drops the selection (asked for 2026-08-29).
 *
 * **The claim is about the next gesture, not about a flag**, so this asserts what the next
 * drag *draws*. A shape is selected as it is drawn, and a press on empty picture with
 * something selected only deselects — so picking a pen and drawing took two drags, and the
 * first of them produced nothing.
 *
 * That a colour still restyles the selection instead is `MarkupRestyleUiTest`'s: the pen is
 * the one row that means "the next shape", and it is the only one that deselects.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupPenDeselectUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a drag right after choosing a pen draws, rather than only deselecting`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                drag(Offset(40f, 40f), Offset(150f, 120f))
                assertEquals(1, harness.session.document.value.markupShapes().size)

                // The arrow is still selected here; the chip is what has to clear it.
                chooseMarkupPen("Rectangle")
                drag(Offset(200f, 180f), Offset(300f, 250f))

                assertEquals(
                    2,
                    harness.session.document.value.markupShapes().size,
                    "the drag after choosing a pen only deselected"
                )
            }
        }
    }
}

/** One stroke drawn on the overlay, from [from] to [to]. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.drag(from: Offset, to: Offset) {
    onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
        moveTo(from)
        press()
        moveTo(to)
        release()
    }
    waitForIdle()
}
