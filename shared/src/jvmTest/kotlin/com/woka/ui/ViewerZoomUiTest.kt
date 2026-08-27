package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.woka.core.data.ScalePreset
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.feature.viewer.shell.percentIn
import com.woka.feature.viewer.shell.zoomBounds
import com.woka.support.VIEWER
import com.woka.support.ViewerUnderTest
import com.woka.support.onViewer
import com.woka.support.poster
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Zoom and pan as a *user* performs them: a double click and a drag on the media
 * area. The arithmetic behind each — `fitPercent`, `stepUp`, `clampOffset`,
 * `rubberBandOffset` — is Phase 2's, and is not repeated through the UI.
 *
 * **A double click does not toggle Fit ↔ Actual.** The plan predicted that;
 * `mediaTapGestures` doubles the current percentage around the cursor, and only
 * falls back to Fit once the ceiling has been reached. Both halves are asserted
 * below, against the behaviour the code actually has.
 *
 * **Ctrl+Scroll is not here.** `mediaViewerGestures` reads
 * `event.keyboardModifiers.isCtrlPressed` off the *pointer* event, and the test
 * framework's mouse injection carries no keyboard state — a held `Ctrl` from
 * `performKeyInput` does not reach a scroll injected separately. The wheel zoom is
 * covered as arithmetic by `ZoomScaleTest` and by hand as TC-IMG-03.
 *
 * The poster is deliberately small (400x300) against the test window, so Fit sits
 * well below the zoom ceiling and there is room to zoom into.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerZoomUiTest {

    private fun MediaViewerState.percent() = sizeMode.percentIn(containerSize, nativeSize)

    @Test
    fun `the viewer opens fitted and knows both its sizes`() = onViewer(1) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster(400, 300))) }
        waitForIdle()
        assertEquals(SizeMode.Preset(ScalePreset.Fit), state.sizeMode)
        assertEquals(400, state.nativeSize.width)
        assertTrue(state.containerSize.width > 0, "the content never reported its panel size")
    }

    @Test
    fun `a double click doubles the zoom`() = onViewer(1) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster(400, 300))) }
        waitForIdle()
        val before = state.percent()

        onNodeWithTag(VIEWER).performMouseInput { doubleClick(center) }
        waitForIdle()
        assertEquals(before * 2, state.percent(), "a double click did not double the zoom")
    }

    @Test
    fun `a double click at the ceiling returns to Fit`() = onViewer(1) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster(400, 300))) }
        waitForIdle()
        state.sizeMode = SizeMode.Zoom(zoomBounds(state.containerSize, state.nativeSize).last)
        waitForIdle()

        onNodeWithTag(VIEWER).performMouseInput { doubleClick(center) }
        waitForIdle()
        assertEquals(SizeMode.Preset(ScalePreset.Fit), state.sizeMode)
        assertEquals(0f, state.offsetX, "returning to Fit left the frame off-centre")
    }

    @Test
    fun `dragging pans the frame while the content overflows`() = onViewer(1) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster(400, 300))) }
        waitForIdle()
        state.sizeMode = SizeMode.Zoom(state.percent() * 4)
        waitForIdle()

        onNodeWithTag(VIEWER).performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(-60f, 0f))
            moveTo(center + Offset(-160f, 0f))
            release()
        }
        waitForIdle()
        assertTrue(abs(state.offsetX) > 0f, "the drag never moved the frame")
    }

    /**
     * This asserted the opposite until 2026-08-28, when the overflow gate came off:
     * a picture that fits the panel used to be pinned to centre, and a drag over it
     * did nothing at all. It is movable now, as far as `panLimits`' floor of half a
     * panel — the same reach it has in the editor.
     */
    @Test
    fun `dragging moves the frame even while the content fits`() = onViewer(1) { items ->
        val state = MediaViewerState(items[0].file.absolutePath)
        setContent { ViewerUnderTest(state, items, ViewerMedia.Still(poster(400, 300))) }
        waitForIdle()

        onNodeWithTag(VIEWER).performMouseInput {
            moveTo(center)
            press()
            moveTo(center + Offset(-60f, -40f))
            moveTo(center + Offset(-160f, -110f))
            release()
        }
        waitForIdle()
        assertTrue(state.offsetX < 0f, "the drag never moved the frame across")
        assertTrue(state.offsetY < 0f, "the drag never moved the frame down")
    }
}
