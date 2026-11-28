package com.woka.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.CarouselVisibility
import com.woka.core.data.MediaItem
import com.woka.core.data.ScalePreset
import com.woka.core.data.StillColorInfo
import com.woka.core.data.WokaSettings
import com.woka.core.ui.components.OriginalCompare
import com.woka.feature.viewer.shell.MediaViewerActiveItemEffects
import com.woka.feature.viewer.shell.MediaViewerNormalControls
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.feature.viewer.shell.ViewerThumbnails
import com.woka.support.VIEWER
import com.woka.support.ViewerUnderTest
import com.woka.support.onUi
import com.woka.support.onViewer
import com.woka.support.poster
import com.woka.support.startKoinOnce
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The viewer's `Original` (RD-502, `#viewer-original`): in the pill only for an edited file,
 * a click toggles and a hold peeks, `\` held peeks, Esc ends a comparison before it closes the
 * viewer, and a still swaps to the frame as shot while it is on.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerOriginalUiTest {

    @BeforeTest
    fun koin() = startKoinOnce()

    @Composable
    private fun Pill(original: OriginalCompare?) {
        MediaViewerNormalControls(
            sizeMode = SizeMode.Preset(ScalePreset.Fit),
            containerSize = IntSize(800, 600),
            nativeSize = IntSize(1600, 1200),
            onSizeModeChange = {},
            onOffsetReset = {},
            carouselMode = CarouselVisibility.AUTO,
            onToggleCarousel = {},
            onEdit = {},
            onInfo = {},
            isInfoOpen = false,
            showHdrMode = false,
            original = original
        )
    }

    @Test
    fun `the pill has no Original for a file without an edit`() = onUi {
        setContent { Pill(null) }
        onNodeWithTag("viewer-original").assertDoesNotExist()
    }

    @Test
    fun `a click toggles and a hold returns on release`() = onUi {
        val compare = OriginalCompare()
        setContent { Pill(compare) }
        onNodeWithTag("viewer-original").performClick()
        waitForIdle()
        assertTrue(compare.active, "a click did not show the original")
        onNodeWithTag("viewer-original").performClick()
        waitForIdle()
        assertFalse(compare.active, "a second click did not return")

        onNodeWithTag("viewer-original").performMouseInput { press() }
        mainClock.advanceTimeBy(400)
        assertTrue(compare.active, "a hold did not show the original")
        onNodeWithTag("viewer-original").performMouseInput { release() }
        waitForIdle()
        assertFalse(compare.active, "the release after a hold did not return")
    }

    @Test
    fun `backslash held peeks and Esc ends a comparison before it closes`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath).also { it.canShowOriginal = true }
        var closed = 0
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster())) { closed++ } }
        waitForIdle()

        onNodeWithTag(VIEWER).performKeyInput { keyDown(Key.Backslash) }
        assertTrue(state.original.active)
        onNodeWithTag("original-label").assertExists()
        onNodeWithTag(VIEWER).performKeyInput { keyUp(Key.Backslash) }
        assertFalse(state.original.active)

        state.original.toggle()
        onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.Escape) }
        assertFalse(state.original.active)
        assertEquals(0, closed, "Esc closed the viewer over a comparison")
        onNodeWithTag(VIEWER).performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, closed)
    }

    @Test
    fun `a still swaps to the frame as shot and back`() = onViewer(2) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { MediaViewerActiveItemEffects(state, items[0], WokaSettings(), false, {}, {}, AsShot) }
        waitForIdle()
        assertEquals(64, state.imageBitmap?.width)

        state.original.toggle()
        waitForIdle()
        assertEquals(30, state.imageBitmap?.width, "the original did not replace the edit")
        state.original.toggle()
        waitForIdle()
        assertEquals(64, state.imageBitmap?.width, "the edit did not come back")
    }
}

/** The edited picture is 64 wide, the original 30: which one is up is read off the width. */
private object AsShot : ViewerThumbnails {
    override suspend fun full(item: MediaItem, frameIndex: Int) = poster(64, 48)
    override suspend fun preview(item: MediaItem, frameIndex: Int) = poster(64, 48)
    override suspend fun original(item: MediaItem, frameIndex: Int) = poster(30, 20)
    override suspend fun sourceSize(item: MediaItem) = IntSize.Zero
    override suspend fun stillColor(item: MediaItem) = StillColorInfo.Sdr
    override fun cachedFull(item: MediaItem, frameIndex: Int) = null
}
