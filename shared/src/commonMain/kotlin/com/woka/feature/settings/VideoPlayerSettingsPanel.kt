package com.woka.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies

/**
 * Video-player settings: playback resume (§4) and the folder track heuristic (§2.6).
 *
 * Reads the repository directly instead of taking value/callback pairs like the
 * older panels. `SettingsOverlay` already threads eighteen parameters through two
 * files, and this section is meant to grow; the player-side panels
 * (`SubtitleCustomizePanel`) already work this way.
 */
@Composable
fun VideoPlayerSettingsPanel() {
    val repository = AppDependencies.settingsRepository
    val settings by repository.settings.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ResumeSettingsSection(settings = settings, repo = repository)

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("System")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = "System media controls",
            subtitle = "Use the keyboard's media keys and show Woka in the system Now Playing panel",
            checked = settings.systemMediaControls,
            onCheckedChange = { repository.setSystemMediaControls(it) }
        )

        Spacer(modifier = Modifier.height(20.dp))
        AmbientSettingsSection(settings = settings, repo = repository)

        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("Tracks")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = "Carry tracks across a folder",
            subtitle = "Open a video with the audio and subtitle tracks last used in its folder",
            checked = settings.carryTracksAcrossFolder,
            onCheckedChange = { repository.setCarryTracksAcrossFolder(it) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        // A file that was ever configured by hand keeps that choice for good, which
        // is the point — but it means changing your mind library-wide needs a way
        // out that is not "visit every file".
        SettingsDangerRow(
            label = "Forget saved tracks for all files",
            confirmLabel = "Forget them",
            hint = "Every video goes back to its container defaults",
            enabled = settings.trackPrefsPerFile.isNotEmpty() ||
                settings.trackPrefsPerFolder.isNotEmpty(),
            onConfirm = { repository.clearAllTrackPrefs() }
        )
    }
}
