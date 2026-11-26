package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import com.woka.feature.editor.engine.video.PLAY_HEAD_SPLIT_TAG
import com.woka.feature.editor.engine.video.TIMELINE_RULER_TAG
import com.woka.feature.editor.tools.trim.TrimTool
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
 * The clip lane over a real player (RD-301): the scissors on the play head, a click that selects
 * without seeking, and the context menu's delete and `Undo all cuts` — each one history entry.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ClipStripUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `the scissors split at the play head, once`() = onTimeline { holder, edits ->
        holder.state!!.seek(1_500L)
        waitForIdle()
        onNodeWithTag(PLAY_HEAD_SPLIT_TAG).performClick()
        waitForIdle()
        val clips = assertNotNull(TrimTool.clipsIn(edits.document))
        assertEquals(2, clips.size, "one clip did not become two")
        assertEquals(1_500L, clips[0].toMs, "the cut is not at the play head")
        assertEquals(1, edits.commits)
        // On the join there is nowhere to cut: the scissors leave.
        onNodeWithTag(PLAY_HEAD_SPLIT_TAG).assertDoesNotExist()
    }

    @Test
    fun `a click on a clip selects it and leaves the play head where it was`() = onTimeline { holder, edits ->
        val player = holder.state!!
        player.seek(1_000L)
        waitForIdle()
        onNodeWithTag(PLAY_HEAD_SPLIT_TAG).performClick()
        waitForIdle()
        onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { click(centerLeft.copy(x = width * 0.8f, y = height * 0.3f)) }
        waitForIdle()
        assertEquals(TrimTool.clipsIn(edits.document)!![1].id, edits.selectedId, "the second clip was not selected")
        assertTrue(abs(player.currentPositionMs - 1_000L) < 200L, "the click moved the play head to ${player.currentPositionMs}")
        assertEquals(TrimTool.manifest.id to setOf(ToolDomain.Geometry, ToolDomain.Annotate), edits.opened)
    }

    @Test
    fun `the clip menu deletes the clip and the hole closes`() = onTimeline { holder, edits ->
        holder.state!!.seek(1_000L)
        waitForIdle()
        onNodeWithTag(PLAY_HEAD_SPLIT_TAG).performClick()
        waitForIdle()
        onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { rightClick(centerLeft.copy(x = width * 0.1f, y = height * 0.3f)) }
        waitForIdle()
        onNodeWithText("Delete clip").performClick()
        waitForIdle()
        val left = assertNotNull(TrimTool.clipsIn(edits.document))
        assertEquals(1, left.size)
        assertEquals(1_000L, left.single().fromMs, "the wrong clip went")
        assertNull(edits.selectedId, "the selection outlived its clip")
        assertEquals(2, edits.commits)
    }

    @Test
    fun `Undo all cuts in the empty-place menu takes the film back whole`() = onTimeline { holder, edits ->
        holder.state!!.seek(1_000L)
        waitForIdle()
        onNodeWithTag(PLAY_HEAD_SPLIT_TAG).performClick()
        waitForIdle()
        onNodeWithTag(TIMELINE_RULER_TAG).performMouseInput { rightClick(center) }
        waitForIdle()
        onNodeWithText("Undo all cuts").performClick()
        waitForIdle()
        assertNull(TrimTool.clipsIn(edits.document), "the cut survived")
        assertEquals(2, edits.commits)
        onNodeWithTag(TIMELINE_RULER_TAG).performMouseInput { rightClick(center) }
        waitForIdle()
        onNodeWithText("Undo all cuts").assertIsNotEnabled()
    }
}
