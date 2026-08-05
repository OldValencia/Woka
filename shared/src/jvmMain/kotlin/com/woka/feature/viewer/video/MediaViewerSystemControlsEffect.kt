package com.woka.feature.viewer.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.woka.core.data.MediaItem
import com.woka.core.system.MediaCommand
import com.woka.core.system.MediaPlaybackState
import com.woka.core.system.MediaSessionInfo
import com.woka.core.system.SystemMediaSession
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.seek
import com.woka.feature.viewer.video.engine.togglePlayPause
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import com.woka.feature.viewer.shell.MediaViewerState

/**
 * Registers the OS media session while a video is open (§4). Windowed viewer:
 * next/previous walk the folder's **videos** — the carousel also holds images,
 * and advancing onto a still would just park there, the same reason
 * `MediaViewerPlaylist` skips them.
 */
@Composable
internal fun MediaViewerSystemControlsEffect(
    state: MediaViewerState,
    videoState: VideoPlayerState?,
    mediaItems: List<MediaItem>,
    enabled: Boolean
) {
    val videos = remember(mediaItems) {
        mediaItems.filterIsInstance<MediaItem.Video>().map { it.file.absolutePath }
    }
    val index = remember(videos, state.currentId) { videos.indexOf(state.currentId) }

    SystemControlsEffect(
        videoState = videoState,
        file = remember(state.currentId) { File(state.currentId) },
        hasNext = index >= 0 && index < videos.lastIndex,
        hasPrevious = index > 0,
        enabled = enabled,
        onNavigate = { direction ->
            val target = videos.getOrNull(index + direction)
            if (index >= 0 && target != null) {
                state.animationDirection = direction
                state.currentId = target
            }
        }
    )
}

/**
 * Fullscreen window: the same session, handed a single [item] and no folder
 * listing, so it offers no next/previous — which is also what that window's own
 * control bar offers.
 */
@Composable
internal fun FullscreenSystemControlsEffect(
    item: MediaItem,
    videoState: VideoPlayerState?,
    enabled: Boolean
) {
    SystemControlsEffect(
        videoState = videoState,
        file = item.file,
        hasNext = false,
        hasPrevious = false,
        enabled = enabled,
        onNavigate = {}
    )
}

@Composable
private fun SystemControlsEffect(
    videoState: VideoPlayerState?,
    file: File,
    hasNext: Boolean,
    hasPrevious: Boolean,
    enabled: Boolean,
    onNavigate: (Int) -> Unit
) {
    val currentNavigate by rememberUpdatedState(onNavigate)
    var token by remember { mutableStateOf(0L) }

    // Keyed on the player object, so a file switch that briefly builds a second
    // VideoPlayerState hands the session over rather than dropping it — that is
    // what the token in SystemMediaSession is for.
    DisposableEffect(videoState, enabled) {
        if (videoState == null || !enabled) return@DisposableEffect onDispose { }
        val issued = SystemMediaSession.attach { command ->
            applyMediaCommand(videoState, command, currentNavigate)
        }
        token = issued
        onDispose {
            SystemMediaSession.detach(issued)
            token = 0L
        }
    }

    LaunchedEffect(token, file, videoState?.durationMs, hasNext, hasPrevious) {
        if (token == 0L) return@LaunchedEffect
        val duration = videoState?.durationMs ?: 0L
        SystemMediaSession.setSession(token, MediaSessionInfo(file.name, duration, hasNext, hasPrevious))
    }

    // Immediate on every play/pause, then a slow tick for the position. The
    // panel's scrubber does not need frame rate, and reading currentPositionMs in
    // composition would recompose the whole viewer on every decoded frame.
    LaunchedEffect(token, videoState?.isPlaying) {
        if (token == 0L || videoState == null) return@LaunchedEffect
        while (true) {
            val state = MediaPlaybackState(videoState.isPlaying, videoState.currentPositionMs)
            SystemMediaSession.setPlayback(token, state)
            delay(1000.milliseconds)
        }
    }
}

private fun applyMediaCommand(
    videoState: VideoPlayerState,
    command: MediaCommand,
    navigate: (Int) -> Unit
) {
    when (command) {
        MediaCommand.PlayPause -> videoState.togglePlayPause()
        MediaCommand.Play -> if (!videoState.isPlaying) videoState.togglePlayPause()
        MediaCommand.Pause -> if (videoState.isPlaying) videoState.togglePlayPause()
        MediaCommand.Stop -> {
            if (videoState.isPlaying) videoState.togglePlayPause()
            videoState.seek(0)
        }
        MediaCommand.Next -> navigate(1)
        MediaCommand.Previous -> navigate(-1)
    }
}
