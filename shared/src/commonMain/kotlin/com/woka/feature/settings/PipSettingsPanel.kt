package com.woka.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WokaSettings

/**
 * Picture-in-picture settings.
 *
 * Takes its value and repository as parameters — the older of the two conventions
 * in this package — because `SettingsOverlayContent` already holds both. The one
 * panel that reaches for `AppDependencies` instead, [VideoPlayerSettingsPanel],
 * is the reason it has no UI test: a panel that resolves the container itself
 * cannot be composed over a throwaway repository.
 *
 * The same switch is on the PiP window's own right-click menu, which is where it
 * is wanted in the moment; this is where it is found when the window is closed.
 */
@Composable
fun PipSettingsPanel(settings: WokaSettings, repo: SettingsRepository) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionLabel("Picture in Picture")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = "Always on Top",
            subtitle = "Keep the picture-in-picture window above other apps. " +
                "Not applied while it fills the screen",
            checked = settings.pipAlwaysOnTop,
            onCheckedChange = { repo.setPipAlwaysOnTop(it) }
        )
    }
}
