package com.woka.feature.viewer.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.skipBy

/**
 * Moving around: between files, along a video's timeline, through a frame set,
 * and the volume and zoom keys.
 *
 * Split out of [handleMediaViewerKeyEvent] when the frame strip added
 * `PageUp`/`PageDown` to a file that was already at the size ceiling.
 */

/**
 * `←`/`→` — **file**, not frame. A burst is still one item in the folder, and
 * making the arrows step frames would leave no way to leave the file. With Alt
 * they seek the video instead, as they always have.
 */
internal fun handleArrowKey(
    event: KeyEvent,
    currentId: String,
    mediaItems: List<MediaItem>,
    videoState: VideoPlayerState?,
    step: Int,
    onSkip: (direction: Int) -> Unit,
    onNavigate: (direction: Int, newId: String) -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return true
    if (event.isAltPressed) {
        videoState?.let {
            it.skipBy(step * 5000L)
            onSkip(step)
        }
        return true
    }
    val index = mediaItems.indexOfFirst { it.file.absolutePath == currentId }
    val target = index + step
    if (index >= 0 && target in mediaItems.indices) {
        onNavigate(step, mediaItems[target].file.absolutePath)
    }
    return true
}

/**
 * `PageUp`/`PageDown` — one frame of the current file, and **stopping at the
 * ends rather than wrapping**: a frame set is a short strip the user is reading
 * through, not a carousel, and wrapping from the last shot back to the first
 * reads as a jump to a different picture.
 */
internal fun handleFrameKey(event: KeyEvent, onStepFrame: (Int) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return true
    when (event.key) {
        Key.PageDown -> onStepFrame(1)
        Key.PageUp -> onStepFrame(-1)
    }
    return true
}

/**
 * `Ctrl +` / `Ctrl −` / `Ctrl 0` (Fit) / `Ctrl 1` (Actual) — the standard set,
 * absent until Step 8b.
 */
internal fun handleZoomKeyEvent(event: KeyEvent, onZoom: (ZoomKey) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (!event.isCtrlPressed && !event.isMetaPressed) return false
    val intent = when (event.key) {
        Key.Equals, Key.Plus, Key.NumPadAdd -> ZoomKey.In
        Key.Minus, Key.NumPadSubtract -> ZoomKey.Out
        Key.Zero, Key.NumPad0 -> ZoomKey.Fit
        Key.One, Key.NumPad1 -> ZoomKey.Actual
        else -> return false
    }
    onZoom(intent)
    return true
}
