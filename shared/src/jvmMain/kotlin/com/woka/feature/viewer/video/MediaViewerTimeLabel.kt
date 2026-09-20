package com.woka.feature.viewer.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.woka.app.AppDependencies
import com.woka.core.ui.theme.Theme

/** From here a file is measured in hours, and both its labels say so. */
private const val HOUR_MS = 3_600_000L

/**
 * A true minus, not a hyphen: it sits on the digits' baseline and carries their
 * width, so `−1:35:03` lines up under `1:35:03` instead of shifting by a hair.
 */
private const val MINUS = "−"

/** Whether a file of this length is written with an hours field at all. */
internal fun usesHours(durationMs: Long): Boolean = durationMs >= HOUR_MS

/** The shape a lone value chooses for itself — the seek tooltip and the editor band. */
internal fun formatTime(ms: Long): String = formatTime(ms, usesHours(ms))

/**
 * [withHours] is decided by the *file's* length, never by the value being drawn, so
 * a film's play head reads `0:04:12` rather than `4:12`. Letting each value choose
 * for itself made the left label gain two characters as the film crossed an hour
 * and shove the seek bar sideways mid-playback.
 *
 * The hour is the largest unit and it is not capped: a three-day recording reads
 * `74:12:05`. Days would be a fourth field and a unit nobody reads off a seek bar.
 */
internal fun formatTime(ms: Long, withHours: Boolean): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val seconds = totalSeconds % 60
    if (!withHours) return "${(totalSeconds / 60).pad()}:${seconds.pad()}"
    return "${totalSeconds / 3600}:${(totalSeconds / 60 % 60).pad()}:${seconds.pad()}"
}

private fun Long.pad(): String = toString().padStart(2, '0')

/**
 * Tabular figures, so the width does not breathe as the seconds tick. A face
 * without a `tnum` table ignores it, which is exactly today's behaviour.
 */
@Composable
private fun timeLabelStyle(): TextStyle = TextStyle(
    // 15sp against the row's 16–20dp icons; at 13 it read as a caption beside them.
    fontSize = 15.sp,
    color = Theme.colors.textPrimary,
    fontFeatureSettings = "tnum"
)

/** Calls [position] in its own scope, so a moving play head recomposes only this. */
@Composable
internal fun TimeLabel(withHours: Boolean, position: () -> Long) {
    BasicText(
        text = formatTime(position(), withHours),
        style = timeLabelStyle(),
        maxLines = 1
    )
}

/**
 * The right-hand label: the file's length, or what is left of it.
 *
 * The click **is** the setting — it writes `showRemainingTime` rather than holding
 * a local flag beside it, so the label and the two places that offer the same
 * choice cannot drift apart. The hand cursor is the whole affordance; a label that
 * moved to advertise itself would be motion over a film for no event.
 *
 * [position] is read only in the remaining branch, which is what keeps the total
 * from recomposing at the presenter's frame rate (§10.5). Remaining counts down the
 * *file's* timeline, not wall-clock at the current speed: it has to agree with the
 * bar underneath it.
 */
@Composable
internal fun TrailingTimeLabel(withHours: Boolean, durationMs: Long, position: () -> Long) {
    val repository = remember { AppDependencies.settingsRepository }
    val settings by repository.settings.collectAsState()
    val remaining = settings.showRemainingTime
    BasicText(
        text = if (remaining) {
            MINUS + formatTime(durationMs - position(), withHours)
        } else {
            formatTime(durationMs, withHours)
        },
        modifier = Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { repository.setShowRemainingTime(!remaining) },
        style = timeLabelStyle(),
        maxLines = 1
    )
}
