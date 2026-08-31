package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.tools.markup.MARKUP_OVERLAY_TAG
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

private val SMALL = IntSize(640, 480)
private val LARGE = IntSize(4_000, 3_000)

/**
 * ME-159 — the size a markup shape is **actually placed at**, driven through the editor
 * the way a user reaches it.
 *
 * **This is the test that was missing, and its absence is why the first attempt shipped
 * doing nothing at all.** That attempt derived a default and handed it to the panel, and
 * a unit test over `markupRows` went green — while every picture still produced a 64 px
 * caption, because `EditorTool.defaults()` materialises every key into a `ParamSet` up
 * front, so the derived default was never the one anybody read. Reported by hand,
 * 2026-08-31, on real folders where a HEIC and a screenshot both gave 64.
 *
 * The lesson is in the assertions: they read `markupShapes().single().width` out of the
 * live document after a real click, and nothing between the pen and the shape is stubbed.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupDefaultSizeUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun captionWidth(harness: EditorHarness): Float =
        (harness.session.document.value.markupShapes().single() as MarkupShape.Text).width

    private fun placeCaption(harness: EditorHarness, sourceSize: IntSize): Float {
        var width = 0f
        onUi {
            openMarkupTool(harness, sourceSize)
            chooseMarkupPen("Text")
            clickPicture(Offset(60f, 60f))
            typeIntoCaret("Woka")
            finishCaret()
            width = captionWidth(harness)
        }
        return width
    }

    /** 6 % of the shorter edge, which is 480 here. */
    @Test
    fun `a caption on a small picture starts at the size that picture suggests`() {
        onEditor(listOf(MarkupTool)) { harness ->
            assertEquals(29f, placeCaption(harness, SMALL), 0.01f)
        }
    }

    /** The same 6 %, of 3000 — the number the old constant could not move to. */
    @Test
    fun `a caption on a large picture starts far larger`() {
        onEditor(listOf(MarkupTool)) { harness ->
            assertEquals(180f, placeCaption(harness, LARGE), 0.01f)
        }
    }

    /**
     * The complaint itself, as one assertion: two pictures, two sizes. It fails on the
     * shipped-inert version with both numbers reading 64.
     */
    @Test
    fun `two pictures do not produce the same caption size`() {
        var small = 0f
        var large = 0f
        onEditor(listOf(MarkupTool)) { harness -> small = placeCaption(harness, SMALL) }
        onEditor(listOf(MarkupTool)) { harness -> large = placeCaption(harness, LARGE) }

        assertEquals(29f, small, 0.01f)
        assertEquals(180f, large, 0.01f)
    }

    /** A stroke takes the same road, at its own fraction — 0.6 % of the shorter edge. */
    @Test
    fun `a stroke starts at the size the picture suggests too`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness, LARGE)
                onNodeWithTag(MARKUP_OVERLAY_TAG).performMouseInput {
                    moveTo(Offset(40f, 40f))
                    press()
                    moveTo(Offset(160f, 130f))
                    release()
                }
                waitForIdle()

                assertEquals(18f, harness.session.document.value.markupShapes().single().width, 0.01f)
            }
        }
    }
}
