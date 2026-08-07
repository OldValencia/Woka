package com.woka.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ScalePreset
import com.woka.feature.viewer.shell.MediaViewerContent
import com.woka.feature.viewer.shell.NO_VIEWER_ACTIONS
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.feature.viewer.shell.ViewerTransform
import com.woka.feature.viewer.shell.percentIn
import com.woka.support.poster
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SURFACE = "pan-surface"

/**
 * `panRequiresCtrl` — the gate TC-PIP-02 asked for. In the PiP/fullscreen window a
 * plain left drag belongs to `pipWindowDraggable` and moves the window; before the
 * gate the same drag also panned the picture inside it.
 *
 * Only the plain drag is asserted here. Two neighbouring halves cannot be:
 *
 * - `Ctrl + LMB`, the gesture that *should* pan there. `mediaZoomAndButtonPan`
 *   reads `keyboardModifiers.isCtrlPressed` off the **pointer** event, and a held
 *   `Ctrl` from `performKeyInput` does not attach itself to a separately injected
 *   mouse event — the limitation `ViewerZoomUiTest` records for `Ctrl+Scroll`.
 * - "letting the button up after a window drag is not a tap". That defect only
 *   exists because the *window* travels with the cursor, which leaves the pointer
 *   almost stationary in local coordinates; `pipWindowDraggable` measures it in
 *   screen pixels from `MouseInfo`, and there is no window to move under a test.
 *   A version written here would pass whether the guard existed or not.
 *
 * Both stay manual, as TC-PIP-02.
 */
@OptIn(ExperimentalTestApi::class)
class ViewerPanModeUiTest {

    private class PanState {
        var mode: SizeMode by mutableStateOf(SizeMode.Preset(ScalePreset.Fit))
        var offsetX: Float by mutableStateOf(0f)
        var offsetY: Float by mutableStateOf(0f)
        var container: IntSize by mutableStateOf(IntSize.Zero)
        var native: IntSize by mutableStateOf(IntSize.Zero)
    }

    @Composable
    private fun Surface(pan: PanState, panRequiresCtrl: Boolean) {
        Box(Modifier.fillMaxSize().testTag(SURFACE)) {
            MediaViewerContent(
                media = ViewerMedia.Still(poster(400, 300)),
                animationDirection = 0,
                transform = ViewerTransform(pan.mode, pan.offsetX, pan.offsetY),
                actions = NO_VIEWER_ACTIONS,
                onSizeModeChange = { pan.mode = it },
                onOffsetChange = { x, y -> pan.offsetX = x; pan.offsetY = y },
                onEffectiveScaleChange = { },
                onMetricsChange = { container, native ->
                    pan.container = container
                    pan.native = native
                },
                onZoomHud = { },
                onPlayPause = { },
                onToggleFullscreen = { },
                panRequiresCtrl = panRequiresCtrl
            )
        }
    }

    private fun drag(panRequiresCtrl: Boolean): PanState {
        val pan = PanState()
        runComposeUiTest {
            setContent { Surface(pan, panRequiresCtrl) }
            waitForIdle()
            // Four times Fit, so the content overflows and a pan has somewhere to go.
            pan.mode = SizeMode.Zoom(pan.mode.percentIn(pan.container, pan.native) * 4)
            waitForIdle()

            onNodeWithTag(SURFACE).performMouseInput {
                moveTo(center)
                press()
                moveTo(center + Offset(-60f, 0f))
                moveTo(center + Offset(-160f, 0f))
                release()
            }
            waitForIdle()
        }
        return pan
    }

    @Test
    fun `with the gate on, a plain drag leaves the picture where it was`() {
        val pan = drag(panRequiresCtrl = true)
        assertEquals(0f, pan.offsetX, "the drag panned the picture the PiP window owns")
        assertEquals(0f, pan.offsetY)
    }

    @Test
    fun `with the gate off, the same drag pans - as it does in the main viewer`() {
        val pan = drag(panRequiresCtrl = false)
        assertTrue(abs(pan.offsetX) > 0f, "the drag never reached the pan at all")
    }

}
