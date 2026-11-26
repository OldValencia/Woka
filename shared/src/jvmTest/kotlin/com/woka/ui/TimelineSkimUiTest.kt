package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.woka.feature.editor.engine.video.CLIP_STRIP_TAG
import com.woka.feature.editor.engine.video.TIMELINE_SKIM_BADGE_TAG
import com.woka.feature.editor.engine.video.TIMELINE_SKIM_TAG
import com.woka.support.MainDispatcherRule
import com.woka.support.onTimeline
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Skimming (RD-301): the pointer over the lanes draws a line and a badge with the moment's time
 * and still; it moves no decoder, so the play head stays put. Leaving hides both; while the film
 * plays there is no skim at all.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class TimelineSkimUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `hovering shows the line and the badge, leaving hides both, and the play head stays`() = onTimeline { holder, _ ->
        val player = holder.state!!
        val before = player.currentPositionMs
        onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { moveTo(Offset(width * 0.6f, height * 0.3f)) }
        waitForIdle()
        onNodeWithTag(TIMELINE_SKIM_TAG).assertExists()
        onNodeWithTag(TIMELINE_SKIM_BADGE_TAG).assertExists()
        assertTrue(player.currentPositionMs - before < 200L, "the skim moved the play head")

        onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { moveTo(Offset(-80f, -80f)) }
        waitForIdle()
        onNodeWithTag(TIMELINE_SKIM_TAG).assertDoesNotExist()
        onNodeWithTag(TIMELINE_SKIM_BADGE_TAG).assertDoesNotExist()
    }

    @Test
    fun `there is no skim while the film plays`() = onTimeline { holder, _ ->
        holder.walk.toggle(holder.state!!)
        waitForIdle()
        onNodeWithTag(CLIP_STRIP_TAG).performMouseInput { moveTo(Offset(width * 0.6f, height * 0.3f)) }
        waitForIdle()
        onNodeWithTag(TIMELINE_SKIM_TAG).assertDoesNotExist()
        onNodeWithTag(TIMELINE_SKIM_BADGE_TAG).assertDoesNotExist()
        holder.walk.toggle(holder.state!!)
    }
}
