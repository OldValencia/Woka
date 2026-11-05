package com.woka.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import com.woka.feature.editor.engine.video.VideoEditorBand
import com.woka.feature.editor.engine.video.VideoPlayerHolder
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.support.FixtureTimeline
import com.woka.support.MainDispatcherRule
import com.woka.support.awaitTrue
import com.woka.support.episodeTwoFile
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An outer edge, dragged by mouse on a real 3 s film (reported 2026-11-05): the same edge can
 * be taken again where it was let go, and a drag the strip's width cut short is still written.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class TrimEdgeUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private var bandWidth by mutableStateOf(620.dp)

    private fun onBand(block: ComposeUiTest.(FixtureTimeline) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = VideoPlayerHolder()
        try {
            holder.open(scope, episodeTwoFile(), volume = 0f)
            awaitTrue("a duration to be read", timeoutMs = 20_000L) { (holder.state?.durationMs ?: 0L) > 0L }
            val edits = FixtureTimeline(editing = true)
            onUi {
                setContent { Box(Modifier.width(bandWidth)) { VideoEditorBand(holder, edits.document, edits, Modifier, IntSize.Zero) } }
                waitForIdle()
                block(edits)
            }
        } finally {
            holder.close()
            scope.cancel()
        }
    }

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
    fun `an end let go can be taken again where it lies`() = onBand { edits ->
        val strip = onNodeWithTag(CLIP_STRIP_TAG)
        val w = strip.fetchSemanticsNode().size.width.toFloat()
        drag(strip, w - 2f, w * 0.5f)
        waitForIdle()
        val first = assertNotNull(TrimTool.clipsIn(edits.document)).single().toMs
        drag(strip, w * 0.5f, w * 0.3f)
        waitForIdle()
        val second = assertNotNull(TrimTool.clipsIn(edits.document)).single().toMs
        assertTrue(second < first, "the end let go at half could not be dragged on: $first then $second")
        assertNull(edits.document.pending, "a cut was left in flight")
    }

    @Test
    fun `a drag cut short by a width change is still committed`() = onBand { edits ->
        val strip = onNodeWithTag(CLIP_STRIP_TAG)
        val w = strip.fetchSemanticsNode().size.width.toFloat()
        drag(strip, w - 2f, w * 0.6f) { bandWidth = 600.dp }
        waitForIdle()
        assertNull(edits.document.pending, "the cut stayed in flight, and Done would write none")
        assertEquals(1, edits.commits)
        assertNotNull(TrimTool.clipsIn(edits.document), "nothing was cut")
    }
}
