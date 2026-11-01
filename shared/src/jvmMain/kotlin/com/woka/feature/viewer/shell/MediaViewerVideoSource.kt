package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.woka.core.data.MediaItem
import com.woka.core.data.WokaSettings
import com.woka.feature.viewer.audio.AudioSession
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.shell.frames.FrameStripModel
import com.woka.feature.viewer.video.MediaViewerSystemControlsEffect
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * Which file the video player is actually playing, which is **not always the
 * item the viewer is on**.
 *
 * A Live Photo is a pair on disk — `IMG_1234.HEIC` beside `IMG_1234.MOV` — so
 * selecting its motion frame has to play a different file from the one being
 * viewed. That is the whole reason this is a function rather than
 * `activeItem as? MediaItem.Video` written inline: the still and the thing
 * playing stopped being the same file.
 *
 * Everything else about the viewer stays keyed to [activeItem] deliberately —
 * the info dialog, the carousel and the folder navigation are all about the
 * photograph, and only playback follows the clip.
 */
@Composable
internal fun rememberPlayedItem(
    activeItem: MediaItem?,
    frameIndex: Int,
    strip: FrameStripModel
): MediaItem? = remember(activeItem, frameIndex, strip) {
    val motion = strip.motion
    if (motion != null && strip.isMotionFrame(frameIndex)) MediaItem.Video(motion) else activeItem
}

/**
 * The player for [playedItem], created and released with it, plus the effects
 * that only make sense while one exists.
 *
 * Lifted out of [MediaViewerScreen] whole rather than in pieces: the creation,
 * the disposal and the two playback effects are one lifecycle, and leaving any
 * of them behind would be a leak waiting to be reintroduced.
 */
@Composable
internal fun rememberViewerVideoState(
    playedItem: MediaItem?,
    itemReady: Boolean,
    mediaVolume: Float,
    state: MediaViewerState,
    settings: WokaSettings,
    mediaItems: List<MediaItem>
): VideoPlayerState? {
    val videoState = remember(playedItem, itemReady) {
        when {
            !itemReady -> null
            // A track belongs to the session, which outlives the viewer (AP-003).
            playedItem is MediaItem.Audio -> AudioSession.createOrGet(playedItem, mediaVolume, mediaItems)
            playedItem is MediaItem.Video -> {
                AudioSession.stopForVideo() // one sound source
                PipManager.createOrGetState(playedItem, mediaVolume)
            }
            else -> null
        }
    }
    DisposableEffect(videoState) {
        onDispose { if (!AudioSession.owns(videoState)) PipManager.releaseState(videoState) }
    }
    MediaViewerPlaybackEffects(state, videoState, settings, mediaItems)
    FollowAudioSession(state, mediaItems)
    MediaViewerSystemControlsEffect(state, videoState, mediaItems, settings.systemMediaControls)
    return videoState
}

private class LastPlayed(var path: String?)

/**
 * A track that moves on in the session takes the open viewer with it (AP-003). Only
 * when the viewer was on the track that ended: a track opened here, or reached by
 * the arrows, changes the session itself and must not be pulled back.
 */
@Composable
private fun FollowAudioSession(state: MediaViewerState, mediaItems: List<MediaItem>) {
    val playing = AudioSession.current?.file?.absolutePath
    val last = remember { LastPlayed(playing) }
    LaunchedEffect(playing) {
        val was = last.path
        last.path = playing
        if (playing == null || was != state.currentId) return@LaunchedEffect
        if (mediaItems.none { it.file.absolutePath == playing }) return@LaunchedEffect
        state.animationDirection = 1
        state.currentId = playing
    }
}
