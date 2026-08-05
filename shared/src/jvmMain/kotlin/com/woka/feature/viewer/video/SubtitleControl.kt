package com.woka.feature.viewer.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.video.model.SubtitleSelection
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.track.selectSubtitle
import org.jetbrains.compose.resources.painterResource
import com.woka.feature.viewer.shell.SimpleTooltip

/**
 * Subtitle track toggle icon in the toolbar (§2.2). Only rendered when the file
 * has ≥1 subtitle track (the caller hides it otherwise), so [videoState] is
 * non-null here. Hovering opens SubtitleVerticalOverlay; clicking toggles on/off.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SubtitleControl(
    videoState: VideoPlayerState,
    onHoverChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks = videoState.tracks.mediaTracks.subtitle
    val external = videoState.tracks.externalSubtitles
    val selection = videoState.tracks.subtitleSelection

    Box(modifier = modifier) {
        val isOff = selection == SubtitleSelection.Off
        val icon = if (isOff) Icons.Static.SubtitlesOff else Icons.Static.Subtitles
        val tint = if (isOff) Theme.colors.textPrimary.copy(alpha = 0.5f) else Theme.colors.textPrimary

        SimpleTooltip(if (isOff) "Subtitles: Off" else "Subtitles") {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .onPointerEvent(PointerEventType.Enter) { onHoverChange(true) }
                    .onPointerEvent(PointerEventType.Exit) { onHoverChange(false) }
                    .clickable {
                        // Toggling on prefers an embedded track and falls back to
                        // a sidecar, so a file with only sidecars still toggles.
                        val on = tracks.firstOrNull()
                            ?.let { SubtitleSelection.Embedded(it.index) }
                            ?: external.firstOrNull()?.let { SubtitleSelection.External(it.path) }
                        videoState.selectSubtitle(
                            if (isOff) on ?: SubtitleSelection.Off else SubtitleSelection.Off
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = "Subtitles",
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
