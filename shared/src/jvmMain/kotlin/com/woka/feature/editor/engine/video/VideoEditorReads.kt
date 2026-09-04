package com.woka.feature.editor.engine.video

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.recipe.EditRecipe
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

/**
 * The two questions the video engine asks a file before anything has been edited: how
 * large its picture is, and what it already carries.
 *
 * Split out of `VideoEditorEngine` when ME-288 gave it a save and pushed that file past
 * the size mandate — `ImageEditorReads`' cut, made for the same reason and along the same
 * line: both of these are a read of a **path**, with no engine state and no document, so
 * the engine keeps only the `by lazy` that decides *when* each is asked.
 */

/**
 * One grabber, opened and closed, for the two numbers the fold is addressed in.
 *
 * `probesize` and `analyzeduration` are `SeekPreviewSource`'s: the player's own grabber
 * enumerates the streams properly, and this one needs only enough of a probe to report a
 * size — 4 ms against 59. No audio stream is opened, because `Int.MAX_VALUE` matches
 * none.
 *
 * **Zero on any failure**, never an exception: `RasterSurface` already returns early on a
 * zero-sized source, so a file that cannot be probed draws nothing instead of taking the
 * window down. It is the line `RecipeCarrier` holds for the same reason.
 */
internal fun codedFrameSize(file: File): IntSize = runCatching {
    val grabber = FFmpegFrameGrabber(file).apply {
        setOption("probesize", "2000000")
        setOption("analyzeduration", "1000000")
        audioStream = Int.MAX_VALUE
    }
    try {
        grabber.start()
        IntSize(grabber.imageWidth, grabber.imageHeight)
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}.getOrDefault(IntSize.Zero)

/**
 * What the file carries, so a second sitting continues the first (ME-288).
 *
 * **Frame 0 is the only frame a video has**, as far as an edit is concerned: a container
 * holding several pictures is a still's problem (ME-156), and a video's position is time
 * rather than an index — which is why `EditorFrames()` is left at its one-picture default
 * and the frame band never appears. So the payload's base slot is the whole answer, and
 * `storedDocumentIn` and `storedBase` inherit it from `storedDocument`.
 *
 * Nothing throws: a payload written by a later build reads as no edit rather than
 * refusing to open the file, which is `EditRecipe`'s own line.
 */
internal fun storedVideoDocument(file: File): EditDocument =
    EditRecipe.documentIn(file, 0) ?: EditDocument()
