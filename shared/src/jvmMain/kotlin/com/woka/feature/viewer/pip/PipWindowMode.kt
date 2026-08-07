package com.woka.feature.viewer.pip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/** Width the shrink button gives the PiP window; the height follows the media. */
private const val PIP_WIDTH_DP = 480

/** Gap between the shrunk window and the working area's bottom-right corner. */
private const val PIP_MARGIN_DP = 24

/** Slack when comparing the window against the screen, for rounding and borders. */
private const val EDGE_TOLERANCE_PX = 4

/**
 * Whether the window is a PiP window rather than one covering the screen.
 *
 * **Geometry only.** `Frame.MAXIMIZED_BOTH` is not consulted: dragging the edge of
 * a maximised window can leave the flag set on a window that no longer fills
 * anything, which made the flag useless as a question *and* — because setting a
 * flag that is already set is not a change — useless as an instruction.
 */
fun isPipSized(window: Frame): Boolean {
    val area = workArea(window)
    return window.width < area.width - EDGE_TOLERANCE_PX ||
        window.height < area.height - EDGE_TOLERANCE_PX
}

/** What mode the window is in, and the one action that changes it. */
class PipWindowMode(val isPip: Boolean, val toggle: () -> Unit)

/**
 * [PipWindowMode] for the fullscreen/PiP window.
 *
 * `isPip` follows the **frame**, through its own resize listener, rather than
 * [state]: dragging the edge of a maximised window un-maximises it without Compose
 * noticing, so `state.placement` goes stale and everything derived from it is
 * wrong until something else happens to change it.
 */
@Composable
fun rememberPipWindowMode(
    window: Frame,
    state: WindowState,
    nativeSize: IntSize
): PipWindowMode {
    var isPip by remember(window) { mutableStateOf(isPipSized(window)) }
    DisposableEffect(window) {
        // Covers every route into the other mode — the edge drag, the button, the
        // OS shortcuts — because all of them resize the frame.
        val listener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                isPip = isPipSized(window)
            }
        }
        window.addComponentListener(listener)
        onDispose { window.removeComponentListener(listener) }
    }
    SideEffect { PipManager.isPipWindow = isPip }
    return remember(isPip, nativeSize) {
        PipWindowMode(isPip) {
            val target = if (isPip) workArea(window) else pipBounds(window, nativeSize)
            applyBounds(window, state, target)
        }
    }
}

/** The desktop minus the taskbar: what "fills the screen" means for this window. */
private fun workArea(window: Frame): Rectangle {
    val gc = window.graphicsConfiguration
    val screen = gc.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
    return Rectangle(
        screen.x + insets.left,
        screen.y + insets.top,
        screen.width - insets.left - insets.right,
        screen.height - insets.top - insets.bottom
    )
}

/** A [PIP_WIDTH_DP]-wide box at the media's aspect ratio, in the bottom-right corner. */
private fun pipBounds(window: Frame, nativeSize: IntSize): Rectangle {
    val area = workArea(window)
    val scale = scaleOf(window)
    val ratio = if (nativeSize.width > 0 && nativeSize.height > 0) {
        nativeSize.height.toFloat() / nativeSize.width
    } else {
        9f / 16f
    }
    val width = (PIP_WIDTH_DP * scale).toInt()
    val height = (width * ratio).toInt()
    val margin = (PIP_MARGIN_DP * scale).toInt()
    return Rectangle(
        area.x + area.width - width - margin,
        area.y + area.height - height - margin,
        width,
        height
    )
}

/**
 * Puts the window at [bounds], writing the frame **and** [state].
 *
 * The frame because it is what [isPipSized] measures and what the window manager
 * acts on; `state` so Compose does not later re-apply values that no longer
 * describe the window. Going through `state` alone is what failed — after an edge
 * drag it still reads `Maximized`, so assigning `Maximized` is not a change and
 * its updater never fires.
 *
 * `Frame.NORMAL` rather than `MAXIMIZED_BOTH` even for the full-screen case: an
 * explicit rectangle over the working area is the same picture and does not depend
 * on the window manager agreeing about a flag it may already believe is set.
 */
private fun applyBounds(window: Frame, state: WindowState, bounds: Rectangle) {
    val scale = scaleOf(window)
    window.extendedState = Frame.NORMAL
    window.setBounds(bounds.x, bounds.y, bounds.width, bounds.height)
    state.placement = WindowPlacement.Floating
    state.position = WindowPosition((bounds.x / scale).dp, (bounds.y / scale).dp)
    state.size = DpSize((bounds.width / scale).dp, (bounds.height / scale).dp)
}

/** Device pixels per dp — screen bounds are the former, window placement the latter. */
private fun scaleOf(window: Frame): Float =
    window.graphicsConfiguration.defaultTransform.scaleX.toFloat().coerceAtLeast(1f)
