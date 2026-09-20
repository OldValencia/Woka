package com.woka.feature.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.data.ResumeMode
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WatchedWhen
import com.woka.core.data.WokaSettings
import com.woka.core.ui.components.SegmentedControl

/**
 * Playback resume (§4) and everything fed by it: where "watched" begins, the
 * dimming of started videos, and the way back out of both.
 *
 * Its own file because `VideoPlayerSettingsPanel` was at 125 lines and the watched
 * threshold could not fit inside the mandate — the same move `AmbientSettingsSection`
 * made before it.
 */
@Composable
fun ResumeSettingsSection(settings: WokaSettings, repo: SettingsRepository) {
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
            repo.setResumeMode(
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
            onChange = { repo.setResumeMinDuration(it) }
        )
    }

    // Under Resume playback because they are fed by it: the marks are drawn from
    // the records that setting writes, so with resume off there is nothing to
    // dim and the rows below would be controlling nothing.
    if (settings.resumeMode != ResumeMode.Off) {
        Spacer(modifier = Modifier.height(14.dp))
        WatchedWhenRow(settings = settings, repo = repo)
        Spacer(modifier = Modifier.height(14.dp))
        SettingsToggleRow(
            title = "Dim watched videos",
            subtitle = "Fade the thumbnail of any video you have already started",
            checked = settings.dimWatchedVideos,
            onCheckedChange = { repo.setDimWatchedVideos(it) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsDangerRow(
            label = "Forget watched marks for all videos",
            confirmLabel = "Forget them",
            hint = "Every video goes back to looking unwatched",
            enabled = settings.watchRecords.isNotEmpty(),
            onConfirm = { repo.clearAllWatchRecords() }
        )
    }
}

/**
 * A segmented control rather than a percentage stepper: the choice is between two
 * *intentions*, and the share behind [WatchedWhen.MostOfIt] is a guess that the
 * player refines per file from the container's credits chapter. Exposing 95 % as a
 * number would promise a precision the threshold does not have.
 */
@Composable
private fun WatchedWhenRow(settings: WokaSettings, repo: SettingsRepository) {
    PanelHint("Count a video as watched after")
    Spacer(modifier = Modifier.height(6.dp))
    SegmentedControl(
        options = listOf(MOST_OF_IT, THE_VERY_END),
        selected = when (settings.watchedWhen) {
            WatchedWhen.MostOfIt -> MOST_OF_IT
            WatchedWhen.TheVeryEnd -> THE_VERY_END
        },
        onSelect = { selected ->
            repo.setWatchedWhen(
                if (selected == THE_VERY_END) WatchedWhen.TheVeryEnd else WatchedWhen.MostOfIt
            )
        }
    )
    Spacer(modifier = Modifier.height(6.dp))
    PanelHint(
        if (settings.watchedWhen == WatchedWhen.MostOfIt) {
            "95% of the file, or the start of its end credits when the file names them"
        } else {
            "The last moments of the file, so a video left in its credits stays unwatched"
        }
    )
}

private const val OFF = "Off"
private const val LONG_ONLY = "Long videos"
private const val ALWAYS = "Always"
private const val MOST_OF_IT = "Most of it"
private const val THE_VERY_END = "The very end"
