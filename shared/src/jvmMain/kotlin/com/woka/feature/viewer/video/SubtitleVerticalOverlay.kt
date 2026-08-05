package com.woka.feature.viewer.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import com.woka.feature.viewer.shell.PopoverAnchor
import com.woka.feature.viewer.shell.ToolbarPopover
import com.woka.feature.viewer.video.engine.subtitle.SUBTITLE_FILE_EXTENSIONS
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.track.addExternalSubtitle
import com.woka.feature.viewer.video.engine.track.hideExternalSubtitle
import com.woka.feature.viewer.video.engine.track.selectSubtitle
import com.woka.core.utils.openFilePicker

/**
 * Subtitle popover docked above the toolbar icon (§2.2). The shared [ToolbarPopover]
 * holds it at a fixed width so the List ↔ Customize (§3) view swap never drops the
 * cursor out of the card.
 */
@Composable
fun SubtitleVerticalOverlay(
    isVisible: Boolean,
    bottomPadding: Dp,
    alignment: Alignment = Alignment.BottomEnd,
    player: VideoPlayerState,
    onHover: (Boolean) -> Unit
) {
    // Stays here, not in the caller: "has no subtitles at all" is a property of
    // this video, not of what kind of media the viewer is showing.
    if (!player.tracks.hasAnySubtitle) return
    val tracks = player.tracks.mediaTracks.subtitle
    val external = player.tracks.externalSubtitles
    val selection = player.tracks.subtitleSelection
    var showCustomize by remember { mutableStateOf(false) }

    // Collapse back to the track list whenever the popover closes.
    LaunchedEffect(isVisible) { if (!isVisible) showCustomize = false }

    ToolbarPopover(
        isVisible = isVisible,
        endPadding = PopoverAnchor.Subtitles,
        bottomPadding = bottomPadding,
        alignment = alignment,
        onHover = onHover
    ) {
        AnimatedContent(
            targetState = showCustomize,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "subtitleView"
        ) { customize ->
            if (customize) {
                SubtitleCustomizePanel(onBack = { showCustomize = false })
            } else {
                SubtitlePopoverList(
                    tracks = tracks,
                    external = external,
                    selection = selection,
                    onSelect = { player.selectSubtitle(it) },
                    onAddFile = {
                        openFilePicker(
                            title = "Choose a subtitle file",
                            extensions = SUBTITLE_FILE_EXTENSIONS,
                            initialDirectory = player.file.parent
                        )?.let { player.addExternalSubtitle(it) }
                    },
                    onRemoveFile = { player.hideExternalSubtitle(it) },
                    onCustomize = { showCustomize = true }
                )
            }
        }
    }
}
