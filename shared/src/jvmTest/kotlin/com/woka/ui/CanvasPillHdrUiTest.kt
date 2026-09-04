package com.woka.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntSize
import com.woka.app.shell.CanvasOverrideState
import com.woka.app.shell.CanvasZoom
import com.woka.app.shell.LocalCanvasOverride
import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.feature.viewer.shell.CanvasZoomPill
import com.woka.support.MainDispatcherRule
import com.woka.support.onUi
import com.woka.support.startKoinOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The editor's **own** pill — the one it brings when it was opened from the grid and has no
 * viewer underneath to borrow one from — and whether it offers `HDR ▾` (ME-303).
 *
 * `CanvasPillUiTest` covers the borrowed pill and is 182 lines, so this is a second file
 * rather than two more cases in it. The rule itself is `HdrControlAppliesTest`'s; what is
 * proved here is that this pill asks it, about the colour the engine published.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CanvasPillHdrUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** `HdrModeControl` injects `SettingsRepository`, so the container has to be up. */
    @BeforeTest
    fun koin() = startKoinOnce()

    private val container = IntSize(800, 600)
    private val native = IntSize(1600, 1200)

    /** With a zoom: the pill returns early without one, and would pass every case blank. */
    private fun overrideFor(color: StillColorInfo) = CanvasOverrideState().apply {
        sourceColor = color
        // 95, not the 50 that is this box's Fit: the readout would then say "Fit · 50%".
        zoom = CanvasZoom(
            percent = 95,
            minPercent = 50,
            maxPercent = 1000,
            containerSize = container,
            nativeSize = native,
            onPercent = {},
            onReset = {}
        )
    }

    @Test
    fun `an SDR picture's pill is the size control alone`() {
        onUi {
            val override = overrideFor(StillColorInfo.Sdr)
            setContent {
                CompositionLocalProvider(LocalCanvasOverride provides override) {
                    CanvasZoomPill()
                }
            }

            // The pill is there and populated — an empty one must not pass this.
            onNodeWithText("95%").assertIsDisplayed()
            onAllNodesWithTag("hdr-mode-anchor").fetchSemanticsNodes().also {
                assertEquals(0, it.size, "the HDR control was offered on an SDR picture")
            }
        }
    }

    @Test
    fun `and a PQ picture's pill keeps the dropdown`() {
        onUi {
            val override = overrideFor(StillColorInfo(TransferKind.PQ, isWideGamut = true))
            setContent {
                CompositionLocalProvider(LocalCanvasOverride provides override) {
                    CanvasZoomPill()
                }
            }

            onNodeWithText("95%").assertIsDisplayed()
            onNodeWithTag("hdr-mode-anchor").assertIsDisplayed()
        }
    }
}
