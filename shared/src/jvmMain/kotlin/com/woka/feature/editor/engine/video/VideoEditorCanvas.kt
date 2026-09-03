package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.IntSize
import com.woka.app.AppDependencies
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.RasterSurface
import com.woka.feature.viewer.video.engine.VideoPlayerState
import java.io.File

/**
 * The video canvas: the player's current frame, drawn through the folded document.
 *
 * **It is the composition of two things that already existed** — `VideoPlayerState`
 * publishes an `ImageBitmap` 24–60 times a second and `RasterSurface` draws *an*
 * `ImageBitmap` through a recipe — which is what makes Crop, Transform, Adjust, Filters
 * and Markup work on a moving picture with no code of their own (§3.1).
 *
 * **`acknowledge` is not optional.** `FrameRetention` frees a frame's ~15 MB once every
 * scene has drawn past it, and it is told from the draw scope, exactly as
 * `MediaViewerFrame` tells it. A canvas that draws frames and never acknowledges does
 * not leak slowly: it holds every frame the file has.
 *
 * **The id is read in the same composition as the bitmap**, so the pair reported is the
 * pair drawn. Read in two places they can be reported out of step, which makes the
 * retention rule either too eager or silently useless.
 */
@Composable
internal fun VideoEditorCanvas(
    file: File,
    holder: VideoPlayerHolder,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    /** Whether an operation is in flight — the engine reads it off the document. */
    dragging: Boolean,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    // **The app's own media volume**, read the way the viewer reads it (ME-207). Read
    // once, at the open: the viewer's volume control is retracted while the editor is
    // up, so nothing can move it meanwhile, and the editor has no control of its own.
    val settings = remember { AppDependencies.settingsRepository }
    DisposableEffect(file) {
        holder.open(scope, file, settings.settings.value.mediaVolume)
        onDispose { holder.close() }
    }

    val player = holder.state ?: return
    HoldWhileDragging(player, dragging)
    // Nothing is drawn until the decoder has presented something. Returning is invisible
    // for the frame or two an open takes; drawing a null bitmap is not an option.
    val frame = player.currentFrame ?: return
    val frameId = player.currentFrameId

    RasterSurface(
        source = frame,
        sourceSize = sourceSize,
        recipe = recipe,
        modifier = modifier.drawWithContent {
            drawContent()
            player.frameRetention.acknowledge(frameId)
        }
    )
}

/**
 * The picture stands still for the length of a drag, and resumes only if it was playing
 * (ME-207).
 *
 * **Not `pauseForSeek()` / `resumeAfterSeek()`**, tempting as their remember-and-restore
 * is: those also set the `@Volatile isSeeking` flag, which is the handshake all three
 * decode coroutines read through `PlaybackGeneration`, and a brightness drag is not a
 * seek. One remembered boolean instead.
 *
 * `internal` rather than private so `VideoCanvasHoldUiTest` can drive it against a real
 * player: the rule is four lines and every one of them is a state transition, which is
 * exactly the shape that reads correct and behaves wrong.
 */
@Composable
internal fun HoldWhileDragging(player: VideoPlayerState, dragging: Boolean) {
    var heldForDrag by remember(player) { mutableStateOf(false) }
    LaunchedEffect(dragging, player) {
        when {
            dragging && player.isPlaying -> {
                player.isPlaying = false
                heldForDrag = true
            }
            !dragging && heldForDrag -> {
                player.isPlaying = true
                heldForDrag = false
            }
        }
    }
}

/**
 * One option of a preview-choice row — the **same held frame**, a different recipe.
 *
 * It deliberately does **not** acknowledge the frame it drew. `FrameRetention`'s contract
 * is that a site which fails to acknowledge only ever holds memory back and can never
 * free a frame early, and the canvas above has already reported this id. Fourteen strip
 * thumbnails reporting the same id would be fourteen redundant calls per frame.
 *
 * Decoding nothing and holding nothing is the whole point: ME-261 is what makes this
 * cheap enough to be true of a shader chain as well as of a colour matrix.
 */
@Composable
internal fun VideoEditorThumbnail(
    holder: VideoPlayerHolder,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    modifier: Modifier
) {
    val frame = holder.state?.currentFrame ?: return
    RasterSurface(source = frame, sourceSize = sourceSize, recipe = recipe, modifier = modifier)
}
