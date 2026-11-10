package com.woka.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.video.SOUND_ROW_TAG
import com.woka.feature.editor.engine.video.SOUND_ROW_TOGGLE_TAG
import com.woka.feature.editor.engine.video.SoundRows
import com.woka.feature.editor.engine.video.VideoEditorBand
import com.woka.feature.editor.engine.video.VideoPlayerHolder
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sound row under the strip (ME-241): the speaker right of the readout puts it out and takes
 * it back, and a press on it moves the play head and edits nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SoundRowUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    @AfterTest
    fun hidden() {
        SoundRows.shown = false
    }

    @Test
    fun `the speaker shows the sound row, a press on it seeks, and it hides again`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val holder = VideoPlayerHolder()
        try {
            holder.open(scope, episodeTwoFile(), volume = 0f)
            awaitTrue("a duration to be read", timeoutMs = 20_000L) { (holder.state?.durationMs ?: 0L) > 0L }
            val edits = FixtureTimeline(editing = false)
            onUi {
                setContent { VideoEditorBand(holder, edits.document, edits, Modifier, IntSize.Zero) }
                waitForIdle()
                onNodeWithTag(SOUND_ROW_TAG).assertDoesNotExist()

                onNodeWithTag(SOUND_ROW_TOGGLE_TAG).performClick()
                waitForIdle()
                onNodeWithTag(SOUND_ROW_TAG).assertIsDisplayed()

                onNodeWithTag(SOUND_ROW_TAG).performMouseInput { click(percentOffset(0.75f, 0.5f)) }
                waitForIdle()
                awaitTrue("the play head to move", timeoutMs = 5_000L) { (holder.state?.currentPositionMs ?: 0L) > 0L }
                assertTrue((holder.state?.currentPositionMs ?: 0L) > 0L, "a press on the row did not seek")
                assertEquals(0, edits.commits, "a press on the sound row edited the film")

                onNodeWithTag(SOUND_ROW_TOGGLE_TAG).performClick()
                waitForIdle()
                onNodeWithTag(SOUND_ROW_TAG).assertDoesNotExist()
            }
        } finally {
            holder.close()
            scope.cancel()
        }
    }
}
