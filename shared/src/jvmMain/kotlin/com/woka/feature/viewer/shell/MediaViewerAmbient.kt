package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import com.woka.core.data.SettingsRepository
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The effects that are about the *session* rather than about which picture is open —
 * volume, the auto-hide timer, focus.
 *
 * Lifted out of `MediaViewerScreenEffects.kt` by ME-180, which is the only reason they
 * are apart: that file crossed the size mandate. The seam was free because these read
 * none of the loading state and none of it reads them.
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
}
