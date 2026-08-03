package com.woka.ui.viewer

import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import com.woka.ui.viewer.pip.PipManager
import com.woka.ui.viewer.player.VideoPlayerState
import com.woka.ui.viewer.player.skipBy
import com.woka.ui.viewer.player.togglePlayPause

/**
 * Keyboard shortcuts for the fullscreen / PiP window. Wired via the `Window`'s
 * `onPreviewKeyEvent`, which fires once the window holds OS focus.
 */
fun handlePipKeyEvent(
    event: KeyEvent,
    videoState: VideoPlayerState?,
    onSizeModeChange: (SizeMode) -> Unit,
    onZoomHud: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isCtrlPressed || event.isMetaPressed) {
        val intent = when (event.key) {
            Key.Equals, Key.Plus, Key.NumPadAdd -> ZoomKey.In
            Key.Minus, Key.NumPadSubtract -> ZoomKey.Out
            Key.Zero, Key.NumPad0 -> ZoomKey.Fit
            Key.One, Key.NumPad1 -> ZoomKey.Actual
            else -> null
        }
        if (intent != null) {
            val result = resolveZoomKey(
                intent,
                PipManager.sizeMode,
                PipManager.containerSize,
                PipManager.nativeSize,
                PipManager.offsetX,
                PipManager.offsetY
            )
            PipManager.offsetX = result.offsetX
            PipManager.offsetY = result.offsetY
            onSizeModeChange(result.sizeMode)
            onZoomHud()
            return true
        }
    }
    return when (event.key) {
        Key.Escape -> {
            PipManager.closePip(returnToMain = true)
            true
        }

        Key.Spacebar -> {
            videoState?.togglePlayPause()
            true
        }

        Key.DirectionLeft -> {
            videoState?.skipBy(-5000)
            true
        }

        Key.DirectionRight -> {
            videoState?.skipBy(5000)
            true
        }

        Key.DirectionUp -> {
            videoState?.let { it.updateVolume((it.volume + 0.1f).coerceIn(0f, 1f)) }
            true
        }

        Key.DirectionDown -> {
            videoState?.let { it.updateVolume((it.volume - 0.1f).coerceIn(0f, 1f)) }
            true
        }

        else -> false
    }
}
