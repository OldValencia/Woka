package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.playFrames
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.viewer.image.rememberHdrModeAccess
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whether the editing canvas is running, and the two ways that changes.
 *
 * A tiny object rather than two fields on the engine, because the engine is at the
 * size mandate and this is the whole of the state: an animation opens playing, a click
 * on the canvas toggles it, and a file that has run out of passes stops itself.
 */
internal class EditorPlayback {

    var isPlaying by mutableStateOf(true)
        private set

    fun toggle() {
        isPlaying = !isPlaying
    }

    fun stop() {
        isPlaying = false
    }
}

/**
 * What the canvas was handed, and whether it came out of an animation.
 *
 * The second field exists because ME-116 must not swap a native still in underneath a
 * GIF: an animation is held frame by frame at proxy size under a 96 MB budget, and a
 * native-resolution one is not a thing this canvas will ever hold. Returned rather
 * than re-derived, because the caller cannot tell a proxy from an animation frame by
 * looking at it — both are bitmaps capped at [PROXY_MAX_PX].
 *
 * [isAnimating] is about the **source**, not about the clock: a paused GIF is still
 * showing an animation frame, and replacing it with the file's native first frame
 * would be a different picture, not a sharper one.
 *
 * [bitmap] is null while the proxy is still being decoded (ME-168). Nothing is drawn
 * then — see [rememberEditorFrame].
 */
@Immutable
internal data class EditorFrame(val bitmap: ImageBitmap?, val isAnimating: Boolean)

/**
 * The picture the canvas draws: one frame of an animation while it runs, or the still
 * proxy for everything else.
 *
 * **Both are decoded off the composition thread** (ME-168). The still used to be read
 * inside a `remember`, which runs *while composing* — 667 ms for the 100 MP source
 * ME-010 measured, ~150 ms for a 24 MP one, and the window frozen for exactly that
 * long on every open and every frame change. It now runs on the same IO dispatcher the
 * animation always used, and the canvas draws nothing until it lands.
 *
 * **Nothing is the right placeholder here.** §4.1 mounts the editor over a frozen,
 * still-mounted viewer, so an empty picture box shows the picture the user was already
 * looking at. A spinner would cover it with less.
 *
 * **The still comes first and never goes away.** The animation is read behind it and
 * takes over when it arrives. A file that is not an animation, or is too large to hold,
 * simply never gets one.
 *
 * The clock is `playFrames`, the same loop the viewer's frame index runs on — the
 * timings, the wrap and the repeat count are one implementation, not two.
 *
 * **The index starts where the editor was opened**, which is the frame the viewer was
 * showing (`EditorTarget.frameIndex`): opening the editor on a paused animation and
 * having it jump to frame 0 to start playing is the one thing here that would look
 * like a bug rather than a feature.
 */
@Composable
internal fun rememberEditorFrame(
    file: File,
    frameIndex: Int,
    playback: EditorPlayback
): EditorFrame {
    // Keyed on the HDR view mode as well: since ME-177 it decides what the decoded
    // pixels are, so a change to it is a change to this bitmap (ME-180).
    val hdrMode = rememberHdrModeAccess().current()

    val still by produceState<ImageBitmap?>(null, file, frameIndex, hdrMode) {
        value = withContext(ImageBitmapCache.ioDispatcher) {
            EditorProxy.load(file, frameIndex)
        }
    }

    val animation by produceState<EditorAnimation?>(null, file, hdrMode) {
        value = withContext(ImageBitmapCache.ioDispatcher) { editorAnimationOf(file) }
    }

    val playing = animation ?: return EditorFrame(still, isAnimating = false)
    var index by remember(playing) { mutableStateOf(frameIndex) }

    LaunchedEffect(playing, playback.isPlaying) {
        if (!playback.isPlaying) return@LaunchedEffect
        playFrames(
            frames = playing.frames,
            repeatCount = playing.repeatCount,
            frameAt = { index },
            show = { index = it },
            onFinished = { playback.stop() }
        )
    }

    return EditorFrame(playing.bitmapAt(index) ?: still, isAnimating = true)
}
