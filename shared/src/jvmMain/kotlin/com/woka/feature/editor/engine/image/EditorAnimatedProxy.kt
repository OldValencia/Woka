package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.playFrames
import com.woka.core.ui.image.ImageBitmapCache
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
 * The picture the canvas draws: one frame of an animation while it runs, or the still
 * proxy for everything else.
 *
 * **The still comes first and never goes away.** It is decoded synchronously, as it
 * always was, so the canvas has a picture on its first composition; the animation is
 * read on the IO dispatcher behind it and takes over when it arrives. A file that is
 * not an animation, or is too large to hold, simply never gets one.
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
): ImageBitmap {
    val still = remember(file, frameIndex) { EditorProxy.load(file, frameIndex) }

    val animation by produceState<EditorAnimation?>(null, file) {
        value = withContext(ImageBitmapCache.ioDispatcher) { editorAnimationOf(file) }
    }

    val playing = animation ?: return still
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

    return playing.bitmapAt(index) ?: still
}
