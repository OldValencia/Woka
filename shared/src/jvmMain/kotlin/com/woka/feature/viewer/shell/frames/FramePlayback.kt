package com.woka.feature.viewer.shell.frames

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.woka.core.data.playFrames
import com.woka.feature.viewer.shell.MediaViewerState

/**
 * The clock an animated GIF or WebP has never had (animated-stills), wired to the
 * viewer's own frame number.
 *
 * **It moves one number.** `MediaViewerState.frameIndex` already says which frame is on
 * screen and the decode effect is already keyed on it, so playing an animation is
 * advancing that index on the frame's own timing — no second decode path, no player, and
 * the frame strip keeps working exactly as it did because it reads the same number.
 * The arithmetic of durations and repeats is `playFrames`, which the editor's canvas
 * runs too.
 *
 * **Nothing runs for a still.** A photograph, a HEIC burst and a multi-page TIFF have no
 * durations ([FrameStripModel.isAnimated] is false), and this returns before its first
 * delay — the common case pays a comparison.
 *
 * [isFrozen] is "something is drawn over this viewer": the editor mounts above it and
 * leaves it composed, so without this the clock would keep decoding frames nobody can
 * see for as long as the editing lasts — and would hand back a different frame from the
 * one Cancel was supposed to return to.
 *
 * The first pass is as slow as the decoder: each frame is decoded when it is due, and skia
 * has to walk earlier frames to build a later one. From the second pass on every frame is
 * an `ImageBitmapCache` hit, so a loop settles into its declared speed.
 */
@Composable
internal fun FramePlaybackEffect(
    state: MediaViewerState,
    strip: FrameStripModel,
    isFrozen: Boolean = false
) {
    LaunchedEffect(state.activeItemPath, strip, state.isFramePlaying, isFrozen) {
        if (isFrozen || !strip.isAnimated || !state.isFramePlaying) return@LaunchedEffect
        playFrames(
            frames = strip.frames,
            repeatCount = strip.repeatCount,
            frameAt = { state.frameIndex },
            show = { state.frameIndex = it },
            onFinished = { state.isFramePlaying = false }
        )
    }
}

/**
 * "Play/pause was asked for" — by a click on the picture or by Space, which raise the
 * same event (`MediaViewerState.playPauseEvent`).
 *
 * Keyed on the counter rather than on a boolean, because two taps in a row have to be
 * two toggles; `ViewerEvent` exists for exactly that and its own doc says why a
 * timestamp did not work. Seq 0 is "never raised" and must not toggle anything on the
 * first composition.
 */
@Composable
internal fun FramePauseRequestEffect(state: MediaViewerState, strip: FrameStripModel) {
    val seq = state.playPauseEvent.seq
    LaunchedEffect(seq) {
        if (seq == 0 || !strip.isAnimated) return@LaunchedEffect
        state.isFramePlaying = !state.isFramePlaying
    }
}
