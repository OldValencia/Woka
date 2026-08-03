package com.woka.ui.viewer

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
import com.woka.ui.viewer.player.SYNC_STEP_MS
import com.woka.ui.viewer.player.VideoPlayerState
import com.woka.ui.viewer.player.formatSyncOffset
import com.woka.ui.viewer.player.resetSync
import com.woka.ui.viewer.player.setAudioDelay
import com.woka.ui.viewer.player.setSubtitleDelay

/**
 * `SYNC` section of the Settings ⚙ popover (§3): shifts subtitles or audio against
 * the picture for releases whose streams were muxed from mismatched sources.
 *
 * Each row is only offered when the file actually has that stream, so the section
 * never shows a stepper that cannot change anything.
 */
@Composable
internal fun PlayerSyncSection(videoState: VideoPlayerState) {
    val hasSubtitles = videoState.hasSubtitle
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
            .background(Color.White.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SyncGlyph("−") { onChange(delayMs - SYNC_STEP_MS) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = formatSyncOffset(delayMs),
                style = TextStyle(
                    color = Color.White,
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
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.72f),
        style = TextStyle(fontSize = 11.sp),
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp)
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
        Text(
            text = symbol,
            style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.03f))
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
        Text(
            text = "Reset sync",
            style = TextStyle(
                color = Color.White.copy(alpha = if (enabled) 0.85f else 0.3f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
