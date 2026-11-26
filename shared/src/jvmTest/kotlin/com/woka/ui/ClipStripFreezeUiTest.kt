package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import com.woka.feature.editor.engine.video.TIMELINE_RULER_TAG
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.viewer.video.engine.seek
import com.woka.support.MainDispatcherRule
import com.woka.support.onTimeline
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `Hold this frame for 2 s` (ME-217; the ❄ on the play head until RD-301): it freezes the frame
 * under the play head and leaves the selection where it was. And the ruler, not the lane, is
 * what moves the play head now.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ClipStripFreezeUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `the menu's hold inserts a freeze and keeps the selection`() = onTimeline { holder, edits ->
        edits.select("source")
        holder.state!!.seek(1_000L)
        waitForIdle()
        onNodeWithTag(TIMELINE_RULER_TAG).performMouseInput { rightClick(center) }
        waitForIdle()
        onNodeWithText("Hold this frame for 2 s").performClick()
        waitForIdle()

        val clips = assertNotNull(TrimTool.clipsIn(edits.document))
        assertEquals(1, clips.count { it.isFreeze }, "no freeze was inserted: $clips")
        assertEquals("source", edits.selectedId, "the freeze took the selection")

        // On the freeze there is nothing to hold again.
        holder.walk.seekTo(holder.state!!, clips, clips.startOf(clips.indexOfFirst { it.isFreeze }) + 500L)
        waitForIdle()
        onNodeWithTag(TIMELINE_RULER_TAG).performMouseInput { rightClick(center) }
        waitForIdle()
        onNodeWithText("Hold this frame for 2 s").assertIsNotEnabled()
    }

    @Test
    fun `the ruler moves the play head and a drag on the only clip does not`() = onTimeline { holder, edits ->
        val player = holder.state!!
        onNodeWithTag(TIMELINE_RULER_TAG).performMouseInput {
            val at = Offset(width / 3f, centerY)
            moveTo(at)
            press()
            moveTo(at + Offset(width / 6f, 0f))
            moveTo(at + Offset(width / 3f, 0f))
            release()
        }
        waitForIdle()
        assertTrue(player.currentPositionMs > player.durationMs / 2, "the play head did not follow the ruler")
        val before = player.currentPositionMs
        onNodeWithTag(CLIP_STRIP_TAG).performMouseInput {
            moveTo(Offset(width * 0.4f, height * 0.3f))
            press()
            moveTo(Offset(width * 0.2f, height * 0.3f))
            release()
        }
        waitForIdle()
        assertTrue(abs(player.currentPositionMs - before) < 200L, "a drag on the lane moved the play head")
        assertNull(TrimTool.clipsIn(edits.document), "the drag cut or moved a clip")
    }
}
