package com.woka.app.shell.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.woka.app.AppDependencies
import com.woka.core.data.MediaKind
import com.woka.core.data.mediaKind
import com.woka.core.system.MediaCommand
import com.woka.core.system.MediaPlaybackState
import com.woka.core.system.MediaSessionInfo
import com.woka.core.system.SystemMediaSession
import com.woka.core.ui.image.encodePng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The slot's source owns the session while it is there, and nothing does once it is
 * emptied: the keys go back to whatever else plays (AP-007). The off switch in Settings
 * holds for this path as for the viewer's.
 *
 * Keyed on the source object, so a new track is a new session handed over through
 * [SystemMediaSession]'s token, not a drop and a re-register.
 */
@Composable
actual fun NowPlayingSystemSession() {
    val nowPlaying = remember { AppDependencies.nowPlaying }
    val settings by AppDependencies.settingsRepository.settings.collectAsState()
    val source = nowPlaying.source
    var token by remember { mutableStateOf(0L) }

    DisposableEffect(source, settings.systemMediaControls) {
        if (source == null || !settings.systemMediaControls) return@DisposableEffect onDispose { }
        val issued = SystemMediaSession.attach { command -> source.apply(command) }
        token = issued
        onDispose {
            SystemMediaSession.detach(issued)
            token = 0L
        }
    }

    // The tags arrive after the slot is claimed, so title and artist are keys too.
    LaunchedEffect(token, source?.title, source?.subtitle) {
        val held = source ?: return@LaunchedEffect
        if (token == 0L) return@LaunchedEffect
        val cover = withContext(Dispatchers.IO) { held.cover()?.encodePng() }
        val info = MediaSessionInfo(
            title = held.title,
            durationMs = 0L,
            hasNext = held.hasNext,
            hasPrevious = held.hasPrevious,
            artist = held.subtitle,
            isVideo = File(held.path).mediaKind() == MediaKind.Video,
            cover = cover
        )
        SystemMediaSession.setSession(token, info)
    }

    LaunchedEffect(token, source?.isPlaying) {
        val held = source ?: return@LaunchedEffect
        if (token == 0L) return@LaunchedEffect
        SystemMediaSession.setPlayback(token, MediaPlaybackState(held.isPlaying, 0L))
    }
}

private fun NowPlayingSource.apply(command: MediaCommand) {
    when (command) {
        MediaCommand.PlayPause -> togglePlayPause()
        MediaCommand.Play -> if (!isPlaying) togglePlayPause()
        MediaCommand.Pause -> if (isPlaying) togglePlayPause()
        MediaCommand.Stop -> stop()
        MediaCommand.Next -> next()
        MediaCommand.Previous -> previous()
    }
}
