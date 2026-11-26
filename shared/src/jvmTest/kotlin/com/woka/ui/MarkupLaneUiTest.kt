package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.TimeWindow
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import com.woka.feature.editor.engine.video.MARKUP_LANE_TAG
import com.woka.feature.editor.engine.video.markupOpIn
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.MarkupWindowsParam
import com.woka.feature.viewer.video.engine.seek
import com.woka.support.FixtureTimeline
import com.woka.support.MainDispatcherRule
import com.woka.support.onTimeline
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Markup lane on a real 3 s film (RD-301): an untimed caption spans the film, a box shown
 * from 0.5 s to 1.5 s sits in a second row; a drag moves its window as one step, an end snaps
 * to the play head, and a click opens Markup with the shape selected.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupLaneUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private val caption = MarkupShape.Text("t", 0xFFFFFFFFL, 2f, MarkupPoint(0.5f, 0.8f), "Nowhere else to be.")
    private val box = MarkupShape.Rectangle("r", 0xFFFF0000L, 2f, MarkupPoint(0.1f, 0.1f), MarkupPoint(0.3f, 0.3f))
    private val film = EditDocument(
        listOf(
            ToolOp(
                MarkupTool.manifest.id,
                ParamSet().with(MarkupTool.Shapes, listOf(caption, box)).with(MarkupWindowsParam, mapOf("r" to TimeWindow(500L, 1_000L)))
            )
        )
    )

    private fun windowOf(edits: FixtureTimeline, id: String): TimeWindow? =
        markupOpIn(edits.document)?.params?.get(MarkupWindowsParam)?.get(id)

    /** The lane's x for a timeline [ms] of a film [totalMs] long, one clip across it. */
    private fun ComposeUiTest.xOf(ms: Long, totalMs: Long): Float =
        onNodeWithTag(MARKUP_LANE_TAG).fetchSemanticsNode().size.width * ms / totalMs.toFloat()

    /** The middle of row [row]: 30 dp bars, 4 dp apart. */
    private fun ComposeUiTest.rowY(row: Int): Float = with(density) { (34.dp * row + 15.dp).toPx() }

    @Test
    fun `an untimed shape spans the whole film`() = onTimeline(document = film) { _, _ ->
        val lane = onNodeWithTag(CLIP_STRIP_TAG).fetchSemanticsNode().size.width
        val bar = onNodeWithTag("editor-markup-bar-t").fetchSemanticsNode().size.width
        assertTrue(bar >= lane - 2, "the caption's bar is $bar px of a $lane px lane")
    }

    @Test
    fun `a drag on the body moves the window as one step`() = onTimeline(document = film) { holder, edits ->
        val total = holder.state!!.durationMs
        onNodeWithTag(MARKUP_LANE_TAG).performMouseInput {
            val y = rowY(1)
            moveTo(Offset(xOf(1_000L, total), y))
            press()
            repeat(8) { step -> moveTo(Offset(xOf(1_000L + 75L * (step + 1), total), y)) }
            release()
        }
        waitForIdle()
        val moved = windowOf(edits, "r")!!
        assertTrue(moved.atMs in 1_000L..1_200L, "the box now appears at ${moved.atMs}")
        assertEquals(1_000L, moved.holdMs, "the move changed how long it holds")
        assertEquals(1, edits.commits)
        assertNull(edits.document.pending)
    }

    @Test
    fun `an end let go near the play head lands on it`() = onTimeline(document = film) { holder, edits ->
        val total = holder.state!!.durationMs
        holder.state!!.seek(2_000L)
        waitForIdle()
        onNodeWithTag(MARKUP_LANE_TAG).performMouseInput {
            val y = rowY(1)
            moveTo(Offset(xOf(1_500L, total) - 3f, y))
            press()
            repeat(8) { step -> moveTo(Offset(xOf(1_500L + 60L * (step + 1), total), y)) }
            release()
        }
        waitForIdle()
        assertEquals(TimeWindow(500L, 1_500L), windowOf(edits, "r"))
    }

    @Test
    fun `a click selects the shape and opens Markup, and writes nothing`() = onTimeline(document = film) { holder, edits ->
        val total = holder.state!!.durationMs
        onNodeWithTag(MARKUP_LANE_TAG).performMouseInput { click(Offset(xOf(1_000L, total), rowY(1))) }
        waitForIdle()
        assertEquals("r", edits.selectedShape)
        assertEquals(MarkupTool.manifest.id to null, edits.opened)
        assertEquals(0, edits.commits)
    }
}
