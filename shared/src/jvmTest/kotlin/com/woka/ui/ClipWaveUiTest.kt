package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import com.woka.feature.editor.engine.video.CLIP_WAVE_TAG
import com.woka.feature.editor.engine.video.PLAY_HEAD_SPLIT_TAG
import com.woka.feature.editor.tools.sound.SoundTool
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.viewer.video.engine.seek
import com.woka.support.MainDispatcherRule
import com.woka.support.onTimeline
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each clip's wave (RD-301; a sound row under the strip behind a speaker until then, ME-241):
 * every clip draws one along its foot, with no switch to find, and a click on it selects the
 * clip and opens Sound — as the prototype's click on `.clip-wave` does.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ClipWaveUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `every clip carries its wave and a click on it opens Sound`() = onTimeline { holder, edits ->
        onAllNodesWithTag(CLIP_WAVE_TAG).assertCountEquals(1)
        holder.state!!.seek(1_000L)
        waitForIdle()
        onNodeWithTag(PLAY_HEAD_SPLIT_TAG).performClick()
        waitForIdle()
        onAllNodesWithTag(CLIP_WAVE_TAG).assertCountEquals(2)

        // The foot of the lane, 9 dp up: inside the 18 dp wave strip.
        onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { click(centerLeft.copy(x = width * 0.8f, y = height - 9.dp.toPx())) }
        waitForIdle()
        assertEquals(TrimTool.clipsIn(edits.document)!![1].id, edits.selectedId)
        assertEquals(SoundTool.manifest.id to null, edits.opened)
    }
}
