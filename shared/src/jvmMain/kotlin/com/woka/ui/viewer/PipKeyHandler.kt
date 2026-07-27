package com.woka.ui.viewer

import androidx.compose.ui.input.key.*
import com.woka.ui.viewer.pip.PipManager
import com.woka.ui.viewer.player.VideoPlayerState

/**
 * Keyboard shortcuts for the fullscreen / PiP window. Wired via the `Window`'s
 * `onPreviewKeyEvent`, which fires once the window holds OS focus.
 */
fun handlePipKeyEvent(event: KeyEvent, videoState: VideoPlayerState?): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
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
            videoState?.let { it.seek((it.currentPositionMs - 5000).coerceAtLeast(0)) }
            true
        }

        Key.DirectionRight -> {
            videoState?.let { it.seek((it.currentPositionMs + 5000).coerceAtMost(it.durationMs)) }
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
