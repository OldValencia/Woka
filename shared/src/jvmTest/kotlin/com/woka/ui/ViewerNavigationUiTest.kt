package com.woka.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.support.VIEWER
import com.woka.support.ViewerUnderTest
import com.woka.support.onViewer
import com.woka.support.poster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Moving between files in the viewer, by button and by key.
 *
 * "Does not wrap" is asserted the way the tree expresses it: `MediaViewerToolbar`
 * composes the Prev button only when `hasPrev`, so at the first file the node does
 * not exist at all. That is stronger than clicking and checking nothing moved —
 * it also catches a button that is drawn but dead.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerNavigationUiTest {

    @Test
    fun `the viewer opens on the file it was given`() = onViewer(3) { items ->
        val state = MediaViewerState(items[1].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster())) }
        onNodeWithTag(VIEWER).assertIsDisplayed()
        assertEquals(items[1].file.absolutePath, state.currentId)
    }

    @Test
    fun `Next and Previous step one file at a time`() = onViewer(3) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster())) }

        onNodeWithContentDescription("Next").performClick()
        waitForIdle()
        assertEquals(items[1].file.absolutePath, state.currentId)

        onNodeWithContentDescription("Next").performClick()
        waitForIdle()
        assertEquals(items[2].file.absolutePath, state.currentId)

        onNodeWithContentDescription("Previous").performClick()
        waitForIdle()
        assertEquals(items[1].file.absolutePath, state.currentId)
    }

    @Test
    fun `the ends of the playlist have no button to walk off`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster())) }
        onNodeWithContentDescription("Previous").assertDoesNotExist()
        onNodeWithContentDescription("Next").assertIsDisplayed()

        onNodeWithContentDescription("Next").performClick()
        waitForIdle()
        onNodeWithContentDescription("Next").assertDoesNotExist()
        onNodeWithContentDescription("Previous").assertIsDisplayed()
    }

    @Test
    fun `a single file has neither button`() = onViewer(1) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster())) }
        onNodeWithContentDescription("Previous").assertDoesNotExist()
        onNodeWithContentDescription("Next").assertDoesNotExist()
    }

    @Test
    fun `the arrow keys walk the playlist and stop at both ends`() = onViewer(3) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster())) }

        onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(items[0].file.absolutePath, state.currentId, "left wrapped at the first file")

        repeat(3) {
            onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
        }
        assertEquals(items[2].file.absolutePath, state.currentId, "right wrapped at the last file")

        onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(items[1].file.absolutePath, state.currentId)
    }

    @Test
    fun `Escape closes on the file that is open, not the one it started on`() =
        onViewer(3) { items ->
            var closedOn: String? = null
            val state = MediaViewerState(items[0].file.absolutePath)
            setContent {
                ViewerUnderTest(state, items, ViewerMedia.Still(poster())) { closedOn = it }
            }
            onNodeWithContentDescription("Next").performClick()
            waitForIdle()
            assertNull(closedOn, "the viewer closed on a navigation")

            onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertEquals(items[1].file.absolutePath, closedOn)
        }
}
