package com.woka.ui

import com.woka.feature.editor.tools.trim.startOf
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
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

/**
 * The ❄ on the play head (ME-217, moved off the clip bar 2026-11-04): it freezes the frame
 * under the play head and leaves the selection where it was.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ClipStripFreezeUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `the play head's freeze inserts a hold and keeps the selection`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = VideoPlayerHolder()
        try {
            holder.open(scope, episodeTwoFile(), volume = 0f)
            awaitTrue("a duration to be read", timeoutMs = 20_000L) { (holder.state?.durationMs ?: 0L) > 0L }
            val edits = FixtureTimeline(editing = true)
            onUi {
                // Room above the band, as in the editor: the selected clip's bar floats there,
                // and pushed down onto the strip it would take the click meant for ❄.
                setContent { VideoEditorBand(holder, edits.document, edits, Modifier.padding(top = 80.dp), IntSize.Zero) }
                waitForIdle()
                edits.select("source")
                holder.state!!.seek(1_000L)
                waitForIdle()
                onNodeWithTag("editor-strip-freeze").performClick()
                waitForIdle()

                val clips = assertNotNull(TrimTool.clipsIn(edits.document))
                assertEquals(1, clips.count { it.isFreeze }, "no freeze was inserted: $clips")
                assertEquals("source", edits.selectedId, "the freeze took the selection")

                // On the freeze both actions are off: nothing to cut, nothing to freeze again.
                holder.walk.seekTo(holder.state!!, clips, clips.startOf(clips.indexOfFirst { it.isFreeze }) + 500L)
                waitForIdle()
                onNodeWithTag("editor-strip-split").assertHasNoClickAction()
                onNodeWithTag("editor-strip-freeze").assertHasNoClickAction()
            }
        } finally {
            holder.close()
            scope.cancel()
        }
    }

    /** 2026-11-04: with the actions on its line the play head could not be dragged in Trim. */
    @Test
    fun `the play head is dragged in Trim, beside its actions`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = VideoPlayerHolder()
        try {
            holder.open(scope, episodeTwoFile(), volume = 0f)
            awaitTrue("a duration to be read", timeoutMs = 20_000L) { (holder.state?.durationMs ?: 0L) > 0L }
            val edits = FixtureTimeline(editing = true)
            val player = holder.state!!
            onUi {
                setContent { VideoEditorBand(holder, edits.document, edits, Modifier, IntSize.Zero) }
                waitForIdle()
                player.seek(player.durationMs / 3)
                waitForIdle()
                onNodeWithTag(CLIP_STRIP_TAG).performMouseInput {
                    val at = Offset(width / 3f, centerY)
                    moveTo(at)
                    press()
                    moveTo(at + Offset(width / 6f, 0f))
                    moveTo(at + Offset(width / 3f, 0f))
                    release()
                }
                waitForIdle()
                assertTrue(player.currentPositionMs > player.durationMs / 2, "the play head did not follow the drag")
                assertNull(TrimTool.clipsIn(edits.document), "the drag cut or moved a clip instead")
            }
        } finally {
            holder.close()
            scope.cancel()
        }
    }
}

