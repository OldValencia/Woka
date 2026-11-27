package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.woka.app.shell.EditorPresence
import com.woka.core.data.SettingsRepository
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.updateVolume
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The effects that are about the *session* rather than about which picture is open —
 * volume, the auto-hide timer, focus.
 *
 * Lifted out of `MediaViewerScreenEffects.kt` by ME-180, which is the only reason they
 * are apart: that file crossed the size mandate. The seam was free because these read
 * none of the loading state and none of it reads them.
 *
 * ME-201 added one more, and this is where it belongs rather than beside the loading
 * effects: standing still while an editor is open is a fact about the session, and it
 * needs the player, which the other half is called before there is one.
 */

/** Volume persistence, the volume HUD timer, UI auto-hide and initial focus. */
@Composable
internal fun MediaViewerAmbientEffects(
    state: MediaViewerState,
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    settingsRepo: SettingsRepository,
    focusRequester: FocusRequester
) {
    PopoverHoverEffect(state.hover)
    ViewerNativeSizeEffect(state)
    LaunchedEffect(mediaVolume) {
        settingsRepo.setMediaVolume(mediaVolume)
        videoState?.updateVolume(mediaVolume)
    }
    LaunchedEffect(state.lastInteractionTime, videoState?.isPlaying, state.hover.rawTarget) {
        if (videoState?.isPlaying == false || state.hover.rawTarget != HoverTarget.None) {
            state.isUiVisible = true
        } else {
            delay(2500.milliseconds)
            state.isUiVisible = false
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    EditorPauseEffect(videoState)
}

/**
 * The viewer stands still while an editor is over it (ME-201).
 *
 * **The editor's canvas runs a player of its own**, and this viewer stays composed and
 * frozen underneath — which for a photograph costs nothing and for a video means two
 * decoders on one file and two soundtracks over each other. A 4K stream decoded twice is
 * not a subtle waste either.
 *
 * **It restores rather than resumes.** A video already paused when `Edit` was pressed
 * must still be paused on `Cancel`, so what is remembered is whether *this* effect was
 * the one that stopped it.
 *
 * **And it holds the whole time, not only at the door** (found by hand, 2026-11-27): a click
 * that reached the viewer through the editor played the film underneath. Keyed on
 * `isPlaying` too, so any start while the editor is up — a stray click, a media key — is
 * stopped at once. Only the pause made as the editor opened is undone on the way out;
 * [opened] tells that one from the rest.
 *
 * `isPlaying` directly and not `togglePlayPause()`: that one seeks to 0 when the play
 * head is near the end and clears `reachedEnd`, and neither belongs in a pause nobody
 * asked for.
 */
@Composable
internal fun EditorPauseEffect(videoState: VideoPlayerState?) {
    var pausedByEditor by remember(videoState) { mutableStateOf(false) }
    var opened by remember(videoState) { mutableStateOf(false) }
    LaunchedEffect(EditorPresence.isOpen, videoState, videoState?.isPlaying) {
        val player = videoState ?: return@LaunchedEffect
        when {
            EditorPresence.isOpen -> {
                if (player.isPlaying) {
                    player.isPlaying = false
                    if (!opened) pausedByEditor = true
                }
                opened = true
            }
            else -> {
                if (pausedByEditor) player.isPlaying = true
                pausedByEditor = false
                opened = false
            }
        }
    }
}
