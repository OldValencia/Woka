package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.core.data.StillColorInfo
import com.woka.core.ui.components.frames.FrameTransport
import com.woka.core.ui.components.frames.step
import com.woka.feature.editor.FrameClockStart
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.SourceStats
import com.woka.feature.viewer.image.readStillColor
import java.io.File

/**
 * The still-image half of the seam: folds a document into a [RasterRecipe] and,
 * from ME-103 onward, draws it.
 *
 * [recipe] is `internal` and absent from [EditorEngine] deliberately (§3.3) — the
 * shell must not be able to name a raster type, or the video editor inherits a
 * shell that already knows about bitmaps.
 */
internal class ImageEditorEngine(
    private val file: File,
    private val frameIndex: Int,
    private val registry: EditorToolRegistry,
    /** Passed in, never re-derived: the factory has already asked (§5.1). */
    capability: EditCapability,
    /** An animation's clock as the viewer left it (RD-501). */
    clock: FrameClockStart = FrameClockStart()
) : ImageEditorOutputs(file, frameIndex, capability) {

    override val kind = MediaKind.Image

    /** Read lazily, and from the decoder — see [nativeFrameSize]. */
    override val sourceSize: IntSize by lazy { nativeFrameSize(file, frameIndex) }

    /** Lazy for [sourceSize]'s reason, and from FFmpeg for `readStillColor`'s (ME-302). */
    override val sourceColor: StillColorInfo by lazy { readStillColor(file) }

    /** Lazy for [sourceSize]'s reason: a menu item greys out from the format alone. */
    override val frames: EditorFrames by lazy { editorFramesOf(file, frameIndex) }

    override fun storedDocument(): EditDocument = storedDocumentIn(frameIndex)

    /** Any frame's, for the session that walks between them (ME-169). */
    override fun storedDocumentIn(frameIndex: Int) = storedDocumentIn(file, frameIndex)

    override fun storedBase(): EditDocument = storedBaseIn(file)

    override fun recipe(document: EditDocument): RasterRecipe = fold(document, registry, sourceSize)

    /** The frame the recipe produces — a crop's dimensions, not the original's. */
    override fun drawnSize(document: EditDocument): IntSize = recipe(document).outputSize

    /** Where the recipe puts a source pixel, which is what an overlay has to follow. */
    override fun previewMatrix(document: EditDocument): Matrix = recipe(document).matrix

    /** Whether the canvas is running, when the file is something that runs. */
    private val playback = EditorPlayback(frameIndex, clock)

    /** An animation's transport (RD-501); a burst's frames are walked by the scope's strip. */
    override val frameTransport: FrameTransport? get() = playback.takeIf { frames.playsAsAnimation }

    /** Measured off the proxy the canvas already decoded, never by decoding again. */
    private val measured = SourceStatsHolder()

    override val stats: SourceStats? get() = measured.stats

    override fun togglePlayback() = playback.toggle()

    /** `,` / `.` step an animation, always (RD-501); a still leaves them alone. */
    override fun timelineKey(key: Key): Boolean {
        val transport = frameTransport?.takeIf { key == Key.Comma || key == Key.Period } ?: return false
        transport.step(if (key == Key.Period) 1 else -1)
        return true
    }

    /**
     * The proxy is remembered on the file, **not on the document**: editing changes the
     * recipe, never the pixels it is applied to. An animation is the exception, and
     * `rememberEditorFrame` is where it lives.
     */
    @Composable
    override fun Surface(document: EditDocument, modifier: Modifier) =
        ImageCanvas(
            file, frameIndex, playback, sourceSize, recipe(document), modifier,
            onProxy = measured::measureOnce
        )

    /**
     * One option of a filter strip, from the **same proxy** — see `ImageCanvas`.
     *
     * **`fill`, because the box is a square and the picture is not** (2026-09-03). Fitted,
     * a 3:2 photograph drew across the top of its square and left the bottom empty; a look
     * strip is for judging colour rather than composition, so it crops.
     */
    @Composable
    override fun Thumbnail(document: EditDocument, modifier: Modifier) =
        ImageCanvas(file, frameIndex, playback, sourceSize, recipe(document), modifier, fill = true)

}
