package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.woka.data.RepeatMode
import com.woka.ui.theme.Theme

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
    onRepeatCycle: () -> Unit,
    onShuffleToggle: () -> Unit
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
                else Color.White.copy(alpha = 0.08f)
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = TextStyle(
                color = Color.White.copy(alpha = if (active) 1f else 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
