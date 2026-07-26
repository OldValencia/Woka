package io.iris.ui.viewer

import androidx.compose.runtime.Composable
import io.iris.data.SettingsRepository
import io.iris.ui.viewer.pip.PipManager

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
