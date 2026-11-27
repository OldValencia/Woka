package com.woka.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.MediaItem
import com.woka.core.data.StillColorInfo
import com.woka.core.data.WokaSettings
import com.woka.feature.viewer.shell.MediaViewerActiveItemEffects
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.feature.viewer.shell.ViewerThumbnails
import com.woka.feature.viewer.shell.frames.FrameStripModel
import com.woka.support.VIEWER
import com.woka.support.ViewerUnderTest
import com.woka.support.onViewer
import com.woka.support.poster
import com.woka.support.startKoinOnce
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An animation's transport in the viewer (RD-501, `#viewer/gif`): it appears for a file that
 * plays and for nothing else, its slots and `,` / `.` move the viewer's one frame index, and
 * each new animation opens with Loop On at 1×.
 *
 * The clock starts paused in most cases, so the frame on screen moves only when a test
 * moves it; the strip's own clock is `FramePlaybackUiTest`'s.
 */
@OptIn(ExperimentalTestApi::class)
class FrameTransportUiTest {

    /** The picture reads the HDR setting through Koin; without this the test passed by run order. */
    @BeforeTest
    fun koin() = startKoinOnce()

    private fun animation(count: Int = 8, durationMs: Int = 100) = FrameStripModel(
        frames = (0 until count).map {
            ImageFrame(it, if (it == 0) FrameKind.Primary else FrameKind.Sequence, durationMs)
        }
    )

    private fun burst() = FrameStripModel(
        frames = (0..2).map { ImageFrame(it, if (it == 0) FrameKind.Primary else FrameKind.Sequence) }
    )

    @Test
    fun `an animation gets the bar and a burst does not`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath).also { it.isFramePlaying = false }
        var strip by mutableStateOf(animation())
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster()), strip = strip) }
        waitForIdle()
        onAllNodesWithTag("frame-transport").assertCountEquals(1)

        strip = burst()
        waitForIdle()
        onAllNodesWithTag("frame-transport").assertCountEquals(0)
    }

    /** Playing, on frames too long for the clock to move one during the test. */
    @Test
    fun `the step slots and the keys pause and move one frame`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster()), strip = animation(durationMs = 600_000)) }
        waitForIdle()
        assertTrue(state.isFramePlaying)

        onNodeWithTag("frame-transport-forward").performClick()
        waitForIdle()
        assertFalse(state.isFramePlaying, "a step left the animation running")
        assertEquals(1, state.frameIndex)

        repeat(2) { onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.Period) } }
        assertEquals(3, state.frameIndex)
        onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.Comma) }
        assertEquals(2, state.frameIndex)

        repeat(9) { onNodeWithTag("frame-transport-forward").performClick() }
        assertEquals(7, state.frameIndex, "a step wrapped past the last frame")
    }

    @Test
    fun `the label switches to frame numbers and Loop switches off`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath).also { it.isFramePlaying = false }
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster()), strip = animation()) }
        waitForIdle()
        state.frameIndex = 2
        onNodeWithText("0:00.2").assertExists()

        onNodeWithTag("frame-transport-time").performClick()
        onNodeWithText("3 / 8").assertExists()
        assertTrue(state.showFrameNumbers)

        onNodeWithTag("frame-transport-loop").performClick()
        assertFalse(state.isFrameLooping)
    }

    /** Loop and speed are for this file only: the next one opens On at 1× (the author, 2026-11-27). */
    @Test
    fun `the next file opens looping at 1x`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        var active by mutableStateOf<MediaItem>(items[0])
        setContent {
            MediaViewerActiveItemEffects(state, active, WokaSettings(), false, {}, {}, BlankThumbnails)
        }
        waitForIdle()
        state.isFrameLooping = false
        state.frameSpeed = 2f

        active = items[1]
        waitForIdle()
        assertTrue(state.isFrameLooping, "Loop was carried to the next file")
        assertEquals(1f, state.frameSpeed, "speed was carried to the next file")
    }
}

/** Nothing is decoded: the effect under test only needs answers. */
private object BlankThumbnails : ViewerThumbnails {
    override suspend fun full(item: MediaItem, frameIndex: Int) = poster(64, 48)
    override suspend fun preview(item: MediaItem, frameIndex: Int) = poster(64, 48)
    override suspend fun sourceSize(item: MediaItem) = IntSize.Zero
    override suspend fun stillColor(item: MediaItem) = StillColorInfo.Sdr
    override fun cachedFull(item: MediaItem, frameIndex: Int) = null
}
