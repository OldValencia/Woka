package com.woka.ui.viewer

import androidx.compose.runtime.Composable
import com.woka.data.SettingsRepository
import com.woka.ui.viewer.pip.PipManager

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
