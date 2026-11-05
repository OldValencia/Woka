package com.woka.feature.editor.engine.video

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.StillColorInfo
import com.woka.feature.editor.core.EditPayload
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.viewer.video.engine.probe.toColour
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
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
internal fun codedFrameSize(file: File): IntSize =
    onHeader(file) { IntSize(it.imageWidth, it.imageHeight) } ?: IntSize.Zero

/**
 * The film's colour, for the HDR pill (ME-304) — through the same capped probe, because
 * `readStillColor` runs `find_stream_info` over the whole of a film, which `sourceColor`
 * would have done on the UI thread as the editor opened. SDR on any failure.
 */
internal fun codedColour(file: File): StillColorInfo =
    onHeader(file) { it.readStreamHdrInfo().toColour() } ?: StillColorInfo.Sdr

private fun <T> onHeader(file: File, read: (FFmpegFrameGrabber) -> T): T? = runCatching {
    val grabber = FFmpegFrameGrabber(file).apply {
        setOption("probesize", "2000000")
        setOption("analyzeduration", "1000000")
        audioStream = Int.MAX_VALUE
    }
    try {
        grabber.start()
        read(grabber)
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}.getOrNull()

/**
 * What the file carries, so a second sitting continues the first (ME-288) — the whole
 * payload since ME-215: [EditPayload.ops] is the shared layer every clip draws, and
 * [EditPayload.frames] holds each clip's own look under its layer key.
 *
 * Nothing throws: a payload written by a later build reads as no edit rather than
 * refusing to open the file, which is `EditRecipe`'s own line.
 */
internal fun storedVideoPayload(file: File): EditPayload = EditRecipe.read(file) ?: EditPayload()
