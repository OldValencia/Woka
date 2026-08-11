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
     * make "played once each" impossible to honour. [currentId] is deliberately
     * *not* part of the signature for the same reason.
     *
     * It is pinned first instead: the permutation is built at the first EOF, and
     * left to chance the file being watched landed at a random index — with
     * repeat off the walk only ever moves forward, so everything before it was
     * dropped, and one folder in `size` stopped dead on the first file.
     */
    private fun orderFor(
        videoPaths: List<String>,
        shuffle: Boolean,
        currentId: String
    ): List<String> {
        val nextSignature = 31 * videoPaths.hashCode() + shuffle.hashCode()
        if (nextSignature != signature || order.isEmpty()) {
            signature = nextSignature
            order = if (shuffle) {
                listOf(currentId) + (videoPaths - currentId).shuffled()
            } else {
                videoPaths
            }
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
        // Before the order is built, not after: an image or a file from another
        // folder is not part of this walk, and pinning it would put a path into
        // the permutation that does not belong to the folder at all.
        if (currentId !in videoPaths) return null

        val walk = orderFor(videoPaths, shuffle, currentId)
        val index = walk.indexOf(currentId)

        return when {
            index < walk.lastIndex -> walk[index + 1]
            repeat == RepeatMode.Folder -> walk.first()
            else -> null // shuffle without folder-repeat stops once exhausted
        }
    }
}

/**
 * The one bridge from settings to the player, so **every** window that shows a
 * player has to mount it. The fullscreen window did not, and since it keeps the
 * running [VideoPlayerState] handed over by `enterFullscreen`, the field simply
 * kept whatever the main window last wrote: turning repeat off in there left the
 * decoder on Repeat File, looping a file the UI said was set to Off (§6).
 *
 * That window has no folder listing on purpose (see `FullscreenSystemControlsEffect`),
 * so `Repeat.Folder` and shuffle still stop at the end there — a limitation of
 * what it is handed, not a missing effect.
 */
@Composable
internal fun PlaybackModeEffects(videoState: VideoPlayerState?, settings: WokaSettings) {
    LaunchedEffect(videoState, settings.repeatMode) {
        videoState?.repeatMode = settings.repeatMode
    }
    LaunchedEffect(videoState, settings.playbackSpeed) {
        videoState?.setPlaybackSpeed(settings.playbackSpeed)
    }
}

/**
 * The modes plus the EOF auto-advance, for the window that has a folder to walk.
 * Repeat File is the decoder's own business; anything that means "play another
 * file" is navigation, so it is resolved here (§6).
 */
@Composable
internal fun MediaViewerPlaybackEffects(
    state: MediaViewerState,
    videoState: VideoPlayerState?,
    settings: WokaSettings,
    mediaItems: List<MediaItem>
) {
    PlaybackModeEffects(videoState, settings)

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
