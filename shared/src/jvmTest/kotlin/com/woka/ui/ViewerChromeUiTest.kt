package com.woka.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.viewer.shell.HoverTarget
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.support.VIEWER
import com.woka.support.ViewerUnderTest
import com.woka.support.onViewer
import com.woka.support.poster
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The chrome's own behaviour: the idle timer that puts it away, the pointer that
 * brings it back, the popover grace period that overrides both, and the info
 * dialog.
 *
 * This is the one file that composes `MediaViewerAmbientEffects`, which is where
 * the 2.5 s timer lives. Everything here runs on the v2 runner's virtual clock —
 * `waitUntil` and `mainClock.advanceTimeBy` drive the `delay`, so the suite does
 * not spend two and a half real seconds per case.
 *
 * **The info dialog shows no dimensions.** The plan expected them; `FileMetaBlock`
 * renders name, path, size and date, and the pixel dimensions appear only through
 * `StreamInfoSections`, which is reached when `mediaTracks != null` — a video. So
 * the assertion is on the name, and "the dialog states the image's dimensions" is
 * a requirement nothing implements rather than a test that was weakened.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerChromeUiTest {

    @Test
    fun `the chrome is up when the viewer opens`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent {
            ViewerUnderTest(state, items, ViewerMedia.Still(poster()), ambientRepo = SettingsRepositoryImpl())
        }
        assertTrue(state.isUiVisible)
        onNodeWithContentDescription("Next").assertIsDisplayed()
    }

    @Test
    fun `the chrome puts itself away once the pointer goes quiet`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent {
            ViewerUnderTest(state, items, ViewerMedia.Still(poster()), ambientRepo = SettingsRepositoryImpl())
        }
        waitUntil("the idle timer to hide the chrome", 10_000) { !state.isUiVisible }
        assertFalse(state.isUiVisible)
    }

    @Test
    fun `moving the pointer brings the chrome back`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent {
            ViewerUnderTest(state, items, ViewerMedia.Still(poster()), ambientRepo = SettingsRepositoryImpl())
        }
        waitUntil("the idle timer to hide the chrome", 10_000) { !state.isUiVisible }

        // Two moves, and no `waitForIdle` after them. The first is delivered as
        // `Enter` — the handler listens for `Move` — and idling would let the
        // virtual clock run the 2.5 s timer straight back down again.
        onNodeWithTag(VIEWER).performMouseInput {
            moveTo(center)
            moveTo(centerRight)
        }
        assertTrue(state.isUiVisible, "a pointer move did not wake the chrome")
    }

    @Test
    fun `a hovered popover holds the chrome open past the timer`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent {
            ViewerUnderTest(state, items, ViewerMedia.Still(poster()), ambientRepo = SettingsRepositoryImpl())
        }
        state.hover.setHovered(HoverTarget.Volume, true)
        mainClock.advanceTimeBy(6_000)
        waitForIdle()
        assertTrue(state.isUiVisible, "the timer hid the chrome while a popover was hovered")

        state.hover.setHovered(HoverTarget.Volume, false)
        waitUntil("the chrome to hide once the pointer leaves", 10_000) { !state.isUiVisible }
    }

    @Test
    fun `Ctrl+I opens the file information dialog and Close dismisses it`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster())) }

        onNodeWithText("File Information").assertDoesNotExist()
        onNodeWithTag(VIEWER).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.I) } }
        waitForIdle()
        onNodeWithText("File Information").assertIsDisplayed()
        onNodeWithText("Name: ${items[0].file.name}").assertIsDisplayed()

        onNodeWithText("Close").performClick()
        waitForIdle()
        onNodeWithText("File Information").assertDoesNotExist()
    }

    @Test
    fun `the dialog describes the file the viewer moved to`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster())) }
        onNodeWithContentDescription("Next").performClick()
        waitForIdle()
        onNodeWithTag(VIEWER).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.I) } }
        waitForIdle()
        onNodeWithText("Name: ${items[1].file.name}").assertIsDisplayed()
    }
}
