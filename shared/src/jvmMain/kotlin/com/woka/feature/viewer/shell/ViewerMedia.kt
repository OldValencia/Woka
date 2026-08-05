package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * What the viewer is showing.
 *
 * Before this, media in the viewer had no type: it was an `ImageBitmap` plus a
 * nullable `VideoPlayerState`, threaded together through the whole shell, and every
 * consumer answered "is this even a video?" again with `?.` and `if (… != null)`.
 * "An image in the viewer" did not exist as a concept — it was `videoState == null`.
 * That is the root of why the image and video viewers are inseparable.
 *
 * **The decoded frame is deliberately not a constructor value.** `currentFrame` is
 * rewritten by the presenter at the decoder's rate; carrying it here would mint a
 * new [ViewerMedia] every frame and push it down the entire tree. The frame stays a
 * `mutableStateOf` on [VideoPlayerState] and is read at the leaf that draws it.
 */
@Stable
sealed interface ViewerMedia {

    /** Drawn until a decoded frame exists, and what the file-change animation keys on. */
    val poster: ImageBitmap

    /** The media's own pixel dimensions — what the "Actual size" preset means. */
    val nativeSize: IntSize

    @Immutable
    data class Still(override val poster: ImageBitmap) : ViewerMedia {
        override val nativeSize: IntSize
            get() = IntSize(poster.width, poster.height)
    }

    @Stable
    class Video(val player: VideoPlayerState, override val poster: ImageBitmap) : ViewerMedia {

        /** The frame to draw right now; falls back to the poster before the first decode. */
        val frame: ImageBitmap
            get() = player.currentFrame ?: poster

        /**
         * The *stream's* dimensions, not the decoded frame's: video may be decoded
         * smaller than native to fit the surface (§6), and the zoom presets — Actual
         * size above all — still mean native 1:1. Knowledge that used to sit in the
         * middle of `MediaViewerContent`.
         *
         * The fallback chain matters and is unchanged: a video's poster is a ≤400px
         * thumbnail, so reporting it as the native size would make "Actual" wrong
         * until the tracks are probed.
         */
        override val nativeSize: IntSize
            get() {
                val track = player.tracks.mediaTracks.video
                    .firstOrNull { it.index == player.tracks.selectedVideoIndex }
                return if (track?.width != null && track.height != null) {
                    IntSize(track.width, track.height)
                } else {
                    IntSize(frame.width, frame.height)
                }
            }
    }
}

/** The player, when there is one. For the branches that genuinely have to ask. */
val ViewerMedia.player: VideoPlayerState?
    get() = (this as? ViewerMedia.Video)?.player

/**
 * Builds the media the shell shows. Null until the poster has decoded — the player
 * shows its spinner meanwhile, exactly as it did when the bitmap was nullable.
 */
@Composable
fun rememberViewerMedia(poster: ImageBitmap?, player: VideoPlayerState?): ViewerMedia? =
    remember(poster, player) {
        when {
            poster == null -> null
            player != null -> ViewerMedia.Video(player, poster)
            else -> ViewerMedia.Still(poster)
        }
    }
