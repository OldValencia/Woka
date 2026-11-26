package com.woka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onTimeline
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An outer end, dragged by mouse on a real 3 s film with no tab open at all (RD-301: the handles
 * are every tab's): a second drag of the same end trims further, and a drag the lane's width cut
 * short is still written (reported 2026-11-05).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class TrimEdgeUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private var laneWidth by mutableStateOf(620.dp)

    /** Two batches, because a batch is sent when its block ends: [midway] runs between them. */
    private fun ComposeUiTest.drag(strip: SemanticsNodeInteraction, fromX: Float, toX: Float, midway: () -> Unit = {}) {
        val y = strip.fetchSemanticsNode().size.height * 0.4f
        strip.performMouseInput {
            moveTo(Offset(fromX, y))
            press()
            repeat(10) { step -> moveTo(Offset(fromX + (toX - fromX) * (step + 1) / 20, y)) }
        }
        waitForIdle()
        midway()
        waitForIdle()
        strip.performMouseInput {
            repeat(10) { step -> moveTo(Offset(fromX + (toX - fromX) * (step + 11) / 20, y)) }
            release()
        }
    }

    @Test
    fun `the end is trimmed again from where the lane lays it out`() = onTimeline { _, edits ->
        val strip = onNodeWithTag(CLIP_STRIP_TAG)
        val w = strip.fetchSemanticsNode().size.width.toFloat()
        drag(strip, w - 2f, w * 0.5f)
        waitForIdle()
        val first = assertNotNull(TrimTool.clipsIn(edits.document)).single().toMs
        // Let go, the lane shows only what is kept: the end is back at its right edge.
        drag(strip, w - 2f, w * 0.6f)
        waitForIdle()
        val second = assertNotNull(TrimTool.clipsIn(edits.document)).single().toMs
        assertTrue(second < first, "the end could not be dragged on: $first then $second")
        assertNull(edits.document.pending, "a cut was left in flight")
    }

    @Test
    fun `a drag cut short by a width change is still committed`() = onTimeline(width = { laneWidth }) { _, edits ->
        val strip = onNodeWithTag(CLIP_STRIP_TAG)
        val w = strip.fetchSemanticsNode().size.width.toFloat()
        drag(strip, w - 2f, w * 0.6f) { laneWidth = 600.dp }
        waitForIdle()
        assertNull(edits.document.pending, "the cut stayed in flight, and Done would write none")
        assertEquals(1, edits.commits)
        assertNotNull(TrimTool.clipsIn(edits.document), "nothing was cut")
    }
}
