package com.woka.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.feature.viewer.shell.frames.FrameStripModel
import com.woka.support.VIEWER
import com.woka.support.ViewerUnderTest
import com.woka.support.onViewer
import com.woka.support.poster
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private fun burstOf(count: Int) = FrameStripModel(
    frames = (0 until count).map {
        ImageFrame(it, if (it == 0) FrameKind.Primary else FrameKind.Sequence)
    }
)

/**
 * The frame strip: the "there is more than one picture in this file" mechanic,
 * at the level the user meets it.
 *
 * The model is supplied rather than decoded — `FrameStripModel` is a parameter
 * of `MediaViewerPlayer` precisely so this layer never opens a HEIC. What a
 * real file reports is `FrameSetDecodeTest`'s job.
 */
@OptIn(ExperimentalTestApi::class)
class FrameStripUiTest {

    /** The case that must stay free: an ordinary photograph gets no strip. */
    @Test
    fun `a single-frame file renders no strip`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster())) }
        waitForIdle()
        onAllNodesWithContentDescription("Frame 1").assertCountEquals(0)
    }

    @Test
    fun `a multi-frame file renders one thumbnail per frame`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster()), strip = burstOf(5)) }
        waitForIdle()
        (1..5).forEach { onAllNodesWithContentDescription("Frame $it").assertCountEquals(1) }
        assertEquals(5, state.frameCount)
    }

    @Test
    fun `clicking a frame selects it`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster()), strip = burstOf(5)) }
        waitForIdle()
        onNodeWithContentDescription("Frame 3").performClick()
        assertEquals(2, state.frameIndex)
    }

    @Test
    fun `PageDown steps forward and stops at the last frame`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster()), strip = burstOf(3)) }
        waitForIdle()
        repeat(5) { onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.PageDown) } }
        assertEquals(2, state.frameIndex, "PageDown ran past the end")
    }

    @Test
    fun `PageUp steps back and stops at the first frame`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        state.frameIndex = 2
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster()), strip = burstOf(3)) }
        waitForIdle()
        repeat(5) { onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.PageUp) } }
        assertEquals(0, state.frameIndex, "PageUp ran past the start")
    }

    /**
     * `←`/`→` mean **file**, not frame. A burst is one item in the folder, and
     * arrows that stepped frames would leave no way out of the file.
     */
    @Test
    fun `the arrows still change file, not frame`() = onViewer(3) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster()), strip = burstOf(4)) }
        waitForIdle()
        onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(items[1].file.absolutePath, state.currentId)
        assertEquals(0, state.frameIndex, "the arrow moved the frame as well as the file")
    }

    /** A Live Photo's clip is marked, so it is obvious which frame plays. */
    @Test
    fun `the motion frame carries a badge`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        val model = FrameStripModel(
            frames = listOf(ImageFrame(0, FrameKind.Primary), ImageFrame(1, FrameKind.Motion)),
            motion = File(items[0].file.parentFile, "clip.mov")
        )
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster()), strip = model) }
        waitForIdle()
        onAllNodesWithContentDescription("Motion").assertCountEquals(2)
    }

    /**
     * The kind alone is not enough: without the sibling file there is nothing to
     * play, and the screen must not switch the player onto a null.
     */
    @Test
    fun `a motion frame with no sibling file is not treated as one`() {
        val withoutFile = FrameStripModel(
            frames = listOf(ImageFrame(0, FrameKind.Primary), ImageFrame(1, FrameKind.Motion))
        )
        assertFalse(withoutFile.isMotionFrame(1))
    }
}
