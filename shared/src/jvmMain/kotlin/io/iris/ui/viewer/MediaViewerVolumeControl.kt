package io.iris.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.iris.ui.viewer.player.VideoPlayerState
import io.iris.ui.icons.IrisIcons
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.ExperimentalComposeUiApi

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
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            if (videoState?.hasAudio != false) {
                                val delta = event.changes.first().scrollDelta.y
                                val volDelta = if (delta > 0) -0.05f else 0.05f
                                onVolumeChange((mediaVolume + volDelta).coerceIn(0f, 1f))
                                onVolumeHoverChange(true)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        val hasAudio = videoState?.hasAudio != false
        val speakerIcon = when {
            !hasAudio -> IrisIcons.Static.SpeakerSlashFill
            mediaVolume == 0f -> IrisIcons.Static.SpeakerSlashFill
            mediaVolume <= 0.33f -> IrisIcons.Static.SpeakerWave1Fill
            mediaVolume <= 0.66f -> IrisIcons.Static.SpeakerWave2Fill
            else -> IrisIcons.Static.SpeakerWave3Fill
        }
        val speakerTint = if (!hasAudio) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
        
        SimpleTooltip(if (!hasAudio) "Audio track unavailable" else if (mediaVolume == 0f) "Unmute" else "Mute") {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = hasAudio) { 
                        if (mediaVolume > 0f) {
                            onVolumeChange(0f)
                        } else {
                            onVolumeChange(0.5f)
                        }
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
