package com.woka.feature.viewer.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.feature.viewer.video.engine.SUBTITLE_FILE_EXTENSIONS
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.addExternalSubtitle
import com.woka.feature.viewer.video.engine.hideExternalSubtitle
import com.woka.feature.viewer.video.engine.selectSubtitle
import com.woka.core.utils.openFilePicker

/**
 * Standalone vertical subtitle popover overlay that docks above the toolbar icon (§2.2),
 * matching the architecture and animation of VolumeVerticalOverlay (§2.1). Holds a
 * fixed width so the List ↔ Customize (§3) view swap never drops the cursor.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SubtitleVerticalOverlay(
    isVisible: Boolean,
    bottomPadding: Dp,
    alignment: Alignment = Alignment.BottomEnd,
    videoState: VideoPlayerState?,
    onHover: (Boolean) -> Unit
) {
    if (videoState?.tracks?.hasAnySubtitle != true) return
    val tracks = videoState.tracks.mediaTracks.subtitle
    val external = videoState.tracks.externalSubtitles
    val selection = videoState.tracks.subtitleSelection
    var showCustomize by remember { mutableStateOf(false) }

    // Collapse back to the track list whenever the popover closes.
    LaunchedEffect(isVisible) { if (!isVisible) showCustomize = false }

    // Fade only — a vertical slide made the popover briefly overlap the toolbar
    // icons during the transition, which re-fired Enter/Exit and flickered it.
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = alignment) {
            Box(
                modifier = Modifier
                    .padding(end = 136.dp, bottom = bottomPadding)
                    .width(210.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp)
                    .onPointerEvent(PointerEventType.Enter) { onHover(true) }
                    .onPointerEvent(PointerEventType.Exit) { onHover(false) }
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
                            onSelect = { videoState.selectSubtitle(it) },
                            onAddFile = {
                                openFilePicker(
                                    title = "Choose a subtitle file",
                                    extensions = SUBTITLE_FILE_EXTENSIONS,
                                    initialDirectory = videoState.file.parent
                                )?.let { videoState.addExternalSubtitle(it) }
                            },
                            onRemoveFile = { videoState.hideExternalSubtitle(it) },
                            onCustomize = { showCustomize = true }
                        )
                    }
                }
            }
        }
    }
}
