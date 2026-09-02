package com.woka.feature.viewer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.woka.core.ui.theme.Theme
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.feature.viewer.shell.PopoverSectionLabel
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.sync.SYNC_STEP_MS
import com.woka.feature.viewer.video.engine.sync.formatSyncOffset
import com.woka.feature.viewer.video.engine.sync.resetSync
import com.woka.feature.viewer.video.engine.sync.setAudioDelay
import com.woka.feature.viewer.video.engine.sync.setSubtitleDelay

/**
 * `SYNC` section of the Settings ⚙ popover (§3): shifts subtitles or audio against
 * the picture for releases whose streams were muxed from mismatched sources.
 *
 * Each row is only offered when the file actually has that stream, so the section
 * never shows a stepper that cannot change anything.
 */
@Composable
internal fun PlayerSyncSection(videoState: VideoPlayerState) {
    val hasSubtitles = videoState.tracks.hasAnySubtitle
    val hasAudio = videoState.hasAudio
    if (!hasSubtitles && !hasAudio) return

    PopoverSectionLabel("Sync")
    if (hasSubtitles) {
        SyncStepperRow(
            label = "Subtitles",
            delayMs = videoState.subtitleDelayMs,
            onChange = { videoState.setSubtitleDelay(it) }
        )
    }
    if (hasAudio) {
        SyncStepperRow(
            label = "Audio",
            delayMs = videoState.audioDelayMs,
            onChange = { videoState.setAudioDelay(it) }
        )
    }
    SyncResetRow(
        enabled = videoState.subtitleDelayMs != 0L || videoState.audioDelayMs != 0L,
        onReset = { videoState.resetSync() }
    )
}

@Composable
private fun SyncStepperRow(label: String, delayMs: Long, onChange: (Long) -> Unit) {
    SyncRowLabel(label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Theme.viewer.onScrimHover),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SyncGlyph("−") { onChange(delayMs - SYNC_STEP_MS) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BasicText(
                text = formatSyncOffset(delayMs),
                style = TextStyle(
                    color = Theme.viewer.onScrim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        SyncGlyph("+") { onChange(delayMs + SYNC_STEP_MS) }
    }
}

@Composable
private fun SyncRowLabel(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp),
        style = TextStyle(fontSize = 11.sp, color = Theme.viewer.onScrimSecondary)
    )
}

@Composable
private fun SyncGlyph(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(34.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = symbol,
            style = TextStyle(color = Theme.viewer.onScrim, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
    }
}

/** Returns both offsets to zero; dimmed and inert while they already are. */
@Composable
private fun SyncResetRow(enabled: Boolean, onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (enabled) Theme.viewer.onScrimHover else Theme.viewer.onScrimHover.copy(alpha = FADED_PLATE))
            .then(
                if (enabled) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onReset)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = "Reset sync",
            style = TextStyle(
                color = if (enabled) Theme.viewer.onScrimSecondary else Theme.viewer.onScrimDisabled,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
