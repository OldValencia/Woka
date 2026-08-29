package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupColor
import com.woka.feature.editor.tools.markup.markupShapes
import com.woka.support.FixtureEngine
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.toggleControl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Restyling a drawn shape from the inspector, through the real rows (ME-114, asked for
 * 2026-08-24).
 *
 * **The claim is the plumbing**, not the arithmetic — `MarkupRestyleTest` covers that.
 * Selection lives in the tool's scope, which used to be built *inside* the overlay; the
 * inspector could not see it, so choosing a colour with an arrow selected left the arrow
 * alone. That is now one `ToolSelection` owned by `EditorScreen` and read by both, and this
 * is the test that the two halves are looking at the same one.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupRestyleUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** The blue in `MarkupTool`'s palette — the fifth offered swatch. */
    private val blueSwatch = 4

    @Test
    fun `choosing a colour with a shape selected recolours that shape`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = FixtureEngine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-markup").performClick()
                waitForIdle()

                // Drawn is selected, so the pen row is now describing this shape.
                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(40f, 40f))
                    press()
                    moveTo(Offset(150f, 120f))
                    release()
                }
                waitForIdle()
                val before = harness.session.document.value.markupShapes().single()

                // The palette is inside the swatch button since ME-155.
                toggleControl(MarkupTool.Color.key)
                onNodeWithTag("param-color-swatch-$blueSwatch").performClick()
                waitForIdle()

                val after = harness.session.document.value.markupShapes().single()
                assertEquals(before.id, after.id, "recolouring replaced the shape")
                assertEquals(
                    MarkupTool.Color.swatches[blueSwatch],
                    after.color,
                    "the drawn shape ignored the swatch — the inspector cannot see the selection"
                )
                // Geometry untouched: a restyle is not a move.
                assertEquals(before.width, after.width)
            }
        }
    }

    /**
     * With nothing selected the same click sets the pen only, and the shape already on the
     * canvas keeps the colour it was drawn with — which is what makes the two behaviours
     * one coherent rule rather than a surprise.
     */
    @Test
    fun `choosing a colour with nothing selected leaves the drawn shape alone`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = FixtureEngine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                onNodeWithTag("editor-tool-markup").performClick()
                waitForIdle()

                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(40f, 40f))
                    press()
                    moveTo(Offset(150f, 120f))
                    release()
                }
                waitForIdle()
                val drawn = harness.session.document.value.markupShapes().single()

                // A click on empty picture deselects and draws nothing.
                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(400f, 300f))
                    press()
                    release()
                }
                waitForIdle()

                toggleControl(MarkupTool.Color.key)
                onNodeWithTag("param-color-swatch-$blueSwatch").performClick()
                waitForIdle()

                val after = harness.session.document.value.markupShapes().single()
                assertEquals(drawn.color, after.color, "the pen recoloured a shape nobody had selected")
                assertEquals(
                    MarkupTool.Color.swatches[blueSwatch],
                    harness.session.document.value.markupColor(),
                    "the pen itself did not change"
                )
            }
        }
    }
}
