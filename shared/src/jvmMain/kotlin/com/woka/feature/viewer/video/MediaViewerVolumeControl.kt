package com.woka.feature.viewer.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.toggleMute
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource
import com.woka.feature.viewer.shell.SimpleTooltip

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerVolumeControl(
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeHoverChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .onPointerEvent(PointerEventType.Enter) { 
                if (videoState?.hasAudio != false) onVolumeHoverChange(true) 
            }
            .onPointerEvent(PointerEventType.Exit) { 
                if (videoState?.hasAudio != false) onVolumeHoverChange(false) 
            }
            .pointerInput(mediaVolume) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            if (videoState?.hasAudio != false) {
                                val delta = event.changes.first().scrollDelta.y
                                val volDelta = if (delta > 0) -0.05f else 0.05f
                                onVolumeChange((mediaVolume + volDelta).coerceIn(0f, 1f))
                                onVolumeHoverChange(true) // Keeps slider open
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        val hasAudio = videoState?.hasAudio != false
        val speakerIcon = when {
            !hasAudio -> Icons.Static.SpeakerSlashFill
            mediaVolume == 0f -> Icons.Static.SpeakerSlashFill
            mediaVolume <= 0.33f -> Icons.Static.SpeakerWave1Fill
            mediaVolume <= 0.66f -> Icons.Static.SpeakerWave2Fill
            else -> Icons.Static.SpeakerWave3Fill
        }
        val speakerTint = if (!hasAudio) Theme.colors.textPrimary.copy(alpha = 0.5f) else Theme.colors.textPrimary
        
        SimpleTooltip(if (!hasAudio) "Audio track unavailable" else if (mediaVolume == 0f) "Unmute" else "Mute") {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    // Through the player, so the button and the M shortcut share one
                    // idea of where unmuting returns to. It used to come back to a
                    // hardcoded half whatever you had been listening at.
                    .clickable(enabled = hasAudio) {
                        videoState?.toggleMute(mediaVolume, onVolumeChange)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(speakerIcon),
                    contentDescription = "Volume",
                    tint = speakerTint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
