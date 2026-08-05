package com.woka.feature.viewer.pip

import androidx.compose.runtime.Composable
import com.woka.core.data.SettingsRepository
import com.woka.feature.viewer.shell.MediaViewerFullscreenWindow

@Composable
actual fun GlobalPipWindow(settingsRepo: SettingsRepository) {
    if (PipManager.isPipActive && PipManager.activeItem != null) {
        MediaViewerFullscreenWindow(
            item = PipManager.activeItem!!,
            videoState = PipManager.videoState,
            settingsRepo = settingsRepo
        )
    }
}
