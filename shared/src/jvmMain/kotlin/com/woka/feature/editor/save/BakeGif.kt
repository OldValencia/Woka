package com.woka.feature.editor.save

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.viewer.image.displayable
import com.woka.feature.viewer.image.forwardFrames
import com.woka.feature.viewer.image.stillDecoderFor
import java.io.File

/**
 * Every frame of a GIF through one recipe, written back as a GIF in [directory] — what
 * Copy hands over for an edited GIF, so a chat receives the animation the person sees.
 *
 * One recipe for all frames: an animation takes no per-frame edit
 * (`EditorFrames.allowsPerFrame`), so every frame comes out at the same size. Each frame is
 * drawn by [renderAtNativeScale], the render the canvas and the photo save use, and the
 * frames are walked forward once (`forwardFrames`) — one frame in memory at a time.
 */
internal fun bakeGif(source: File, sourceSize: IntSize, recipe: RasterRecipe, directory: File): File {
    val stamp = SourceStamp.of(source)
    val set = stillDecoderFor(source).frameSet(source)
    val delays = set.frames.map { it.durationMs ?: 0 }
    val target = File(directory, "${source.nameWithoutExtension}.gif")
    try {
        GifWriter(target, recipe.outputSize, delays, set.repeatCount).use { writer ->
            forwardFrames(source) { _, frame ->
                renderAtNativeScale(displayable(source, frame), sourceSize, recipe).use(writer::add)
            }
            writer.finish()
        }
        check(stamp.matches(source)) { "${source.name} changed while it was being copied" }
        return target
    } catch (failure: Throwable) {
        target.delete()
        throw failure
    }
}
