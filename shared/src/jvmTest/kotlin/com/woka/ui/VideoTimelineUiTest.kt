package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.woka.app.AppDependencies
import com.woka.feature.editor.engine.video.EDITOR_TIMELINE_TAG
import com.woka.feature.editor.engine.video.TIMELINE_RESIZER_TAG
import com.woka.support.MainDispatcherRule
import com.woka.support.onTimeline
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The docked timeline over a real player (RD-301): it draws this player's readout and transport,
 * its height is dragged and kept in `settings.json`, and the zoom factor shows only while zoomed.
 *
 * Where a scrub lands is not asserted here: a swipe measured in pixels tests the test's arithmetic
 * more than the timeline's; the ruler's drag is `ClipStripFreezeUiTest`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class VideoTimelineUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** The decode loop reads `AppDependencies.settingsRepository` for playback resume. */
    @BeforeTest
    fun koin() = startKoinOnce()

    @Test
    fun `the timeline draws this player's readout and its play button starts and stops it`() = onTimeline { holder, _ ->
        val player = holder.state!!
        onNodeWithTag(EDITOR_TIMELINE_TAG).assertExists()
        onNodeWithTag("editor-timeline-readout").assertExists()
        assertFalse(player.isPlaying, "the holder did not open paused")
        onNodeWithTag("editor-timeline-play").performClick()
        waitForIdle()
        assertTrue(player.isPlaying, "the play button did not start it")
        onNodeWithTag("editor-timeline-play").performClick()
        waitForIdle()
        assertFalse(player.isPlaying, "the play button did not stop it again")
    }

    @Test
    fun `the resizer's drag up makes the timeline taller and the height is kept`() {
        val settings = AppDependencies.settingsRepository
        try {
            onTimeline { _, _ ->
                val before = onNodeWithTag(EDITOR_TIMELINE_TAG).fetchSemanticsNode().size.height
                onNodeWithTag(TIMELINE_RESIZER_TAG).performMouseInput {
                    moveTo(center)
                    press()
                    repeat(6) { moveBy(Offset(0f, -10f)) }
                    release()
                }
                waitForIdle()
                val after = onNodeWithTag(EDITOR_TIMELINE_TAG).fetchSemanticsNode().size.height
                assertTrue(after > before, "the timeline is $after px, was $before")
                assertTrue(settings.settings.value.editorTimelineHeight > 220, "the height was not kept")
            }
        } finally {
            settings.setEditorTimelineHeight(220)
        }
    }

    /**
     * The corpus film is 3 s, shorter than the tightest view (5 s across the lane, `StripZoom`),
     * so + has nothing to show closer: the factor between − and + appears only once zoomed.
     * The zoom's own arithmetic is `StripZoomTest`'s.
     */
    @Test
    fun `the zoom factor shows only while zoomed, and plus cannot zoom past the tightest view`() = onTimeline { _, _ ->
        onNodeWithTag("editor-strip-zoom").assertDoesNotExist()
        onNodeWithTag("editor-timeline-zoom-in").performClick()
        waitForIdle()
        onNodeWithTag("editor-strip-zoom").assertDoesNotExist()
        onNodeWithTag("editor-timeline-zoom-out").assertExists()
    }
}
