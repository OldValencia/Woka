package com.woka.feature.viewer.shell

import com.woka.core.data.MediaItem
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * Hands the running player to the fullscreen window and closes the in-app viewer.
 *
 * Its own function because two callers need it now — the toolbar button and the F
 * shortcut — and inlining it a second time in `MediaViewerScreen` would have taken
 * that file past the §2 size limit. Stills are silently ignored: the fullscreen
 * window exists to carry a *player* across, and there is nothing to carry.
 */
internal fun enterFullscreen(
    state: MediaViewerState,
    videoState: VideoPlayerState?,
    activeItem: MediaItem?,
    onClose: (String) -> Unit
) {
    if (videoState == null || activeItem == null) return
    PipManager.openPip(activeItem, videoState, state.sizeMode, state.offsetX, state.offsetY)
    onClose(state.currentId)
}
