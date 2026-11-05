package com.woka.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.video.VideoEditorBand
import com.woka.feature.editor.engine.video.VideoPlayerHolder
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

/**
 * Clips are picked in every tool once there are two (2026-11-05, ME-267): outside `Clips` a
 * clip's tab selects it and its bar offers `Copy` and `Paste ▾` alone; one clip shows no tab.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ClipLookUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    private fun onStrip(block: (VideoPlayerHolder, FixtureTimeline) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = VideoPlayerHolder()
        try {
            holder.open(scope, episodeTwoFile(), volume = 0f)
            awaitTrue("a duration to be read", timeoutMs = 20_000L) { (holder.state?.durationMs ?: 0L) > 0L }
            block(holder, FixtureTimeline(editing = true))
        } finally {
            holder.close()
            scope.cancel()
        }
    }

    @Test
    fun `outside Clips a cut film's clip offers its look and nothing else`() = onStrip { holder, edits ->
        onUi {
            setContent { VideoEditorBand(holder, edits.document, edits, Modifier, IntSize.Zero) }
            waitForIdle()
            edits.editing = false
            waitForIdle()
            onNodeWithTag("editor-clip-tab-source").assertDoesNotExist()

            edits.editing = true
            holder.state!!.seek(1_000L)
            waitForIdle()
            onNodeWithTag("editor-strip-split").performClick()
            edits.editing = false
            waitForIdle()

            onNodeWithTag("editor-clip-tab-source").performClick()
            waitForIdle()
            onNodeWithTag("editor-clip-copy").assertExists()
            onNodeWithTag("editor-clip-paste").assertExists()
            onNodeWithTag("editor-clip-delete").assertDoesNotExist()
            onNodeWithTag("editor-strip-split").assertDoesNotExist()
        }
    }
}
