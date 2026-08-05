package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.woka.core.data.MediaItem
import com.woka.core.data.RepeatMode
import com.woka.core.data.WokaSettings
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.setPlaybackSpeed

/**
 * Decides what plays after the current file (§6). Shuffle supplies the *order*,
 * repeat decides whether the end of that order *wraps* — the two are independent.
 *
 * Only videos take part. The viewer also shows images, but auto-advancing onto a
 * still would just park there, so they are skipped rather than played.
 *
 * Behaviour:
 * - `Repeat.Folder` — advance through the order, wrapping at the end.
 * - shuffle on, repeat not Folder — advance through the shuffled order, stop at
 *   the end. Every file plays once before any repeats.
 * - neither — return null, i.e. keep the old behaviour of stopping at EOF.
 */
@Stable
class MediaViewerPlaylist {
    private var order: List<String> = emptyList()
    private var signature: Int = 0

    /**
     * The walk order, recomputed only when the folder contents or the shuffle
     * toggle actually change — otherwise a fresh permutation on every EOF would
     * make "played once each" impossible to honour.
     */
    private fun orderFor(videoPaths: List<String>, shuffle: Boolean): List<String> {
        val nextSignature = 31 * videoPaths.hashCode() + shuffle.hashCode()
        if (nextSignature != signature || order.isEmpty()) {
            signature = nextSignature
            order = if (shuffle) videoPaths.shuffled() else videoPaths
        }
        return order
    }

    fun nextPath(
        currentId: String,
        mediaItems: List<MediaItem>,
        shuffle: Boolean,
        repeat: RepeatMode
    ): String? {
        if (repeat != RepeatMode.Folder && !shuffle) return null

        val videoPaths = mediaItems
            .filterIsInstance<MediaItem.Video>()
            .map { it.file.absolutePath }
        if (videoPaths.size < 2) return null

        val walk = orderFor(videoPaths, shuffle)
        val index = walk.indexOf(currentId)
        if (index == -1) return null

        return when {
            index < walk.lastIndex -> walk[index + 1]
            repeat == RepeatMode.Folder -> walk.first()
            else -> null // shuffle without folder-repeat stops once exhausted
        }
    }
}

/**
 * Pushes the playback modes from settings into the player and auto-advances at
 * EOF. Repeat File is the decoder's own business; anything that means "play
 * another file" is navigation, so it is resolved here (§6).
 */
@Composable
internal fun MediaViewerPlaybackEffects(
    state: MediaViewerState,
    videoState: VideoPlayerState?,
    settings: WokaSettings,
    mediaItems: List<MediaItem>
) {
    LaunchedEffect(videoState, settings.repeatMode) {
        videoState?.repeatMode = settings.repeatMode
    }
    LaunchedEffect(videoState, settings.playbackSpeed) {
        videoState?.setPlaybackSpeed(settings.playbackSpeed)
    }

    val playlist = rememberMediaViewerPlaylist()
    LaunchedEffect(videoState?.reachedEnd) {
        if (videoState?.reachedEnd != true) return@LaunchedEffect
        val next = playlist.nextPath(
            currentId = state.currentId,
            mediaItems = mediaItems,
            shuffle = settings.shuffle,
            repeat = settings.repeatMode
        ) ?: return@LaunchedEffect
        state.animationDirection = 1
        state.currentId = next
    }
}

@Composable
private fun rememberMediaViewerPlaylist(): MediaViewerPlaylist = remember { MediaViewerPlaylist() }
