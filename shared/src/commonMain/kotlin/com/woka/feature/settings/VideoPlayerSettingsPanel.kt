package com.woka.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.app.AppDependencies
import com.woka.core.data.ResumeMode
import com.woka.core.ui.theme.Theme
import com.woka.core.ui.components.SegmentedControl

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
        SectionLabel("Resume playback")
        Spacer(modifier = Modifier.height(6.dp))
        PanelHint("Continue a video where you left off instead of starting over")
        Spacer(modifier = Modifier.height(10.dp))
        SegmentedControl(
            options = listOf(OFF, LONG_ONLY, ALWAYS),
            selected = when (settings.resumeMode) {
                ResumeMode.Off -> OFF
                ResumeMode.LongOnly -> LONG_ONLY
                ResumeMode.Always -> ALWAYS
            },
            onSelect = { selected ->
                repository.setResumeMode(
                    when (selected) {
                        LONG_ONLY -> ResumeMode.LongOnly
                        ALWAYS -> ResumeMode.Always
                        else -> ResumeMode.Off
                    }
                )
            }
        )
        // The threshold only means anything in the mode that uses it — shown the
        // same way the zoom stepper appears only under the Zoom default.
        if (settings.resumeMode == ResumeMode.LongOnly) {
            Spacer(modifier = Modifier.height(14.dp))
            PanelHint("Only for videos longer than")
            Spacer(modifier = Modifier.height(6.dp))
            MinutesStepper(
                minutes = settings.resumeMinDurationMinutes,
                onChange = { repository.setResumeMinDuration(it) }
            )
        }

        // Under Resume playback because it is fed by it: the marks are drawn from
        // the records that setting writes, so with resume off there is nothing to
        // dim and the rows below would be controlling nothing.
        if (settings.resumeMode != ResumeMode.Off) {
            Spacer(modifier = Modifier.height(14.dp))
            SettingsToggleRow(
                title = "Dim watched videos",
                subtitle = "Fade the thumbnail of any video you have already started",
                checked = settings.dimWatchedVideos,
                onCheckedChange = { repository.setDimWatchedVideos(it) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsDangerRow(
                label = "Forget watched marks for all videos",
                confirmLabel = "Forget them",
                hint = "Every video goes back to looking unwatched",
                enabled = settings.watchRecords.isNotEmpty(),
                onConfirm = { repository.clearAllWatchRecords() }
            )
        }

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

private const val OFF = "Off"
private const val LONG_ONLY = "Long videos"
private const val ALWAYS = "Always"

@Composable
private fun PanelHint(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = Theme.colors.textSecondary, fontSize = 11.sp)
    )
}
