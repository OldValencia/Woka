package com.woka.feature.viewer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.RepeatMode
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.PopoverSectionLabel

/**
 * Repeat, Shuffle and Playback Speed rows of the Settings ⚙ popover (§6).
 *
 * Repeat is one cycling row rather than three toggles — Off → This file → Folder
 * is a single choice, and three separate switches would let the user express
 * states that do not exist.
 */
@Composable
internal fun PlaybackModeRows(
    repeat: RepeatMode,
    shuffle: Boolean,
    showRemainingTime: Boolean,
    onRepeatCycle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onTimeLabelToggle: () -> Unit
) {
    PopoverSectionLabel("Playback")
    ValueRow(
        label = "Repeat",
        value = repeat.label,
        active = repeat != RepeatMode.Off,
        onClick = onRepeatCycle
    )
    ValueRow(
        label = "Shuffle",
        value = if (shuffle) "On" else "Off",
        active = shuffle,
        onClick = onShuffleToggle
    )
    // The same flag the label itself writes when clicked, and the same one the
    // Settings panel offers — one state, three ways in.
    ValueRow(
        label = "Time left",
        value = if (showRemainingTime) "Remaining" else "Total",
        active = showRemainingTime,
        onClick = onTimeLabelToggle
    )
}

/** Label on the left, current value on the right; the whole row is the target. */
@Composable
private fun ValueRow(label: String, value: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (active) Theme.colors.accent.copy(alpha = 0.30f)
                else Theme.viewer.onScrimHover
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = Theme.viewer.onScrimSecondary, fontSize = 11.sp),
            modifier = Modifier.weight(1f)
        )
        BasicText(
            text = value,
            style = TextStyle(
                color =
                    if (active) Theme.viewer.onScrim else Theme.viewer.onScrimSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
