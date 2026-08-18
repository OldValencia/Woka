package com.woka.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import com.woka.app.shell.CanvasOverrideState
import com.woka.app.shell.CanvasZoom
import com.woka.app.shell.LocalCanvasOverride
import com.woka.core.data.CarouselVisibility
import com.woka.core.data.ScalePreset
import com.woka.feature.viewer.shell.MediaViewerNormalControls
import com.woka.feature.viewer.shell.SizeMode
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The top bar's pill, and what happens to it when the editor opens over the viewer.
 *
 * The editor does not replace this pill — it repoints it (`CanvasOverrideState`), which
 * is the whole reason the carousel eye and `Edit` can animate away instead of being
 * swapped for a second pill that Compose would build from scratch.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CanvasPillUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val container = IntSize(800, 600)
    private val native = IntSize(1600, 1200)

    @Composable
    private fun Pill(override: CanvasOverrideState, onEdit: () -> Unit = {}) {
        CompositionLocalProvider(LocalCanvasOverride provides override) {
            MediaViewerNormalControls(
                sizeMode = SizeMode.Preset(ScalePreset.Fit),
                containerSize = container,
                nativeSize = native,
                onSizeModeChange = {},
                onOffsetReset = {},
                carouselMode = CarouselVisibility.AUTO,
                onToggleCarousel = {},
                onEdit = onEdit,
                onInfo = {},
                isInfoOpen = false
            )
        }
    }

    @Test
    fun `the eye and Edit retract once an editor owns the canvas`() = onUi {
        val override = CanvasOverrideState()
        setContent { Pill(override) }

        onNodeWithContentDescription("Toggle Carousel").assertIsDisplayed()
        onNodeWithContentDescription("Edit").assertIsDisplayed()

        override.editing = true
        waitUntil { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isEmpty() }

        // A carousel you cannot see and an Edit that reopens what is already open.
        onAllNodesWithContentDescription("Toggle Carousel").fetchSemanticsNodes().also {
            assertEquals(0, it.size, "the eye stayed in the pill")
        }
        // Everything else stays, which is the point of borrowing the pill.
        onNodeWithContentDescription("Information").assertIsDisplayed()
        onNodeWithText("Fit · 50%").assertIsDisplayed()
    }

    @Test
    fun `the size control reads the owner that took the canvas over`() = onUi {
        val override = CanvasOverrideState()
        override.zoom = zoomAt(95) {}
        setContent { Pill(override) }

        // Not "Fit · 50%": the viewer underneath is frozen and its own state is
        // no longer what the control is describing.
        onNodeWithText("95%").assertIsDisplayed()
    }

    @Test
    fun `a step stops at the owner's floor, not the viewer's`() = onUi {
        var written = -1
        val override = CanvasOverrideState()
        override.zoom = zoomAt(55) { written = it }
        setContent { Pill(override) }

        onNodeWithText("−").performClick()

        // A tenth down from 55 is 50, and `zoomBounds` would have gone on to 5 — the
        // editor has one mode and Fit is its floor, so that is where the control stops.
        assertEquals(50, written)
    }

    /**
     * Asserted against the published bounds rather than against a number: the canvas's
     * size in a test window is not the one a user has, and Fit follows it.
     */
    @Test
    fun `the editor's canvas hands its zoom to the pill`() {
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                val override = CanvasOverrideState()
                setContent { EditorUnderShell(harness, TopBarAccessoriesState(), override) }
                waitForIdle()

                val zoom = override.zoom
                assertTrue(zoom != null, "the canvas published nothing")
                // Opens at Fit, its own floor.
                assertEquals(zoom.minPercent, zoom.percent)

                zoom.onPercent(zoom.maxPercent)
                waitForIdle()
                assertEquals(zoom.maxPercent, override.zoom?.percent)

                override.zoom?.onReset()
                waitForIdle()
                assertEquals(zoom.minPercent, override.zoom?.percent)
            }
        }
    }

    private fun zoomAt(percent: Int, onPercent: (Int) -> Unit) = CanvasZoom(
        percent = percent,
        minPercent = 50,
        maxPercent = 1000,
        containerSize = container,
        nativeSize = native,
        onPercent = onPercent,
        onReset = {}
    )
}
