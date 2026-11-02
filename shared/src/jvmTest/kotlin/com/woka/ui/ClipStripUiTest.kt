package com.woka.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import com.woka.feature.editor.engine.video.VideoEditorBand
import com.woka.feature.editor.engine.video.VideoPlayerHolder
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.viewer.video.engine.seek
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

/**
 * The strip's buttons over a real player (ME-212, ME-213): the ✂ on the play head and the
 * selected clip's bar, each writing one history entry through the timeline scope.
 *
 * A 3 s corpus file, so every position here is inside it. Drags are by hand, for
 * `VideoBandUiTest`'s reason.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ClipStripUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private fun onStrip(block: (VideoPlayerHolder, FixtureTimeline) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = VideoPlayerHolder()
        try {
            holder.open(scope, episodeTwoFile(), volume = 0f)
            awaitTrue("a duration to be read", timeoutMs = 20_000L) {
                (holder.state?.durationMs ?: 0L) > 0L
            }
            block(holder, FixtureTimeline(editing = true))
        } finally {
            holder.close()
            scope.cancel()
        }
    }

    @Test
    fun `the scissors split at the play head, once`() = onStrip { holder, edits ->
        onUi {
            setContent { VideoEditorBand(holder, edits.document, edits, Modifier, IntSize.Zero) }
            waitForIdle()
            holder.state!!.seek(1_500L)
            waitForIdle()

            onNodeWithTag("editor-strip-split").performClick()
            waitForIdle()
            val clips = assertNotNull(TrimTool.clipsIn(edits.document))
            assertEquals(2, clips.size, "one clip did not become two")
            assertEquals(1_500L, clips[0].toMs, "the cut is not at the play head")
            assertEquals(1, edits.commits)

            onNodeWithTag("editor-strip-split").performClick()
            waitForIdle()
            assertEquals(1, edits.commits, "a split on a join pushed a history entry")
        }
    }

    @Test
    fun `a selected clip is deleted from its bar and the hole closes`() = onStrip { holder, edits ->
        onUi {
            setContent { VideoEditorBand(holder, edits.document, edits, Modifier, IntSize.Zero) }
            waitForIdle()
            holder.state!!.seek(1_000L)
            waitForIdle()
            onNodeWithTag("editor-strip-split").performClick()
            waitForIdle()

            // The first clip is the left third of the strip.
            onNodeWithTag(CLIP_STRIP_TAG).performTouchInput { click(centerLeft.copy(x = width * 0.1f)) }
            waitForIdle()
            assertNotNull(edits.selectedId, "a click on a clip selected nothing")

            onNodeWithTag("editor-clip-delete").performClick()
            waitForIdle()
            val left = assertNotNull(TrimTool.clipsIn(edits.document))
            assertEquals(1, left.size)
            assertEquals(1_000L, left.single().fromMs, "the wrong clip went")
            assertNull(edits.selectedId, "the selection outlived its clip")
            assertEquals(2, edits.commits)
        }
    }

    @Test
    fun `a right-click on the strip takes the cut back to the whole film`() = onStrip { holder, edits ->
        onUi {
            setContent { VideoEditorBand(holder, edits.document, edits, Modifier, IntSize.Zero) }
            waitForIdle()
            holder.state!!.seek(1_000L)
            waitForIdle()
            onNodeWithTag("editor-strip-split").performClick()
            waitForIdle()
            onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { rightClick(center) }
            waitForIdle()
            assertNull(TrimTool.clipsIn(edits.document), "the cut survived the reset")
            assertEquals(2, edits.commits)
            onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { rightClick(center) }
            waitForIdle()
            assertEquals(2, edits.commits, "resetting an uncut film pushed a history entry")
        }
    }
}
