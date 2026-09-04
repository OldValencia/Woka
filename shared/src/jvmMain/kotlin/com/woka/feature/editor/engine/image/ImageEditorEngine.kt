package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.core.data.StillColorInfo
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.SourceStats
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.save.SourceStamp
import com.woka.feature.editor.save.evictCachesFor
import com.woka.feature.viewer.image.readStillColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    override val capability: EditCapability
) : EditorEngine {

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

    internal fun recipe(document: EditDocument): RasterRecipe =
        fold(document, registry, sourceSize)

    /** The frame the recipe produces — a crop's dimensions, not the original's. */
    override fun drawnSize(document: EditDocument): IntSize = recipe(document).outputSize

    /** Where the recipe puts a source pixel, which is what an overlay has to follow. */
    override fun previewMatrix(document: EditDocument): Matrix = recipe(document).matrix

    /** Whether the canvas is running, when the file is something that runs. */
    private val playback = EditorPlayback()

    /** Measured off the proxy the canvas already decoded, never by decoding again. */
    private val measured = SourceStatsHolder()

    override val stats: SourceStats? get() = measured.stats

    override fun togglePlayback() = playback.toggle()

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

    /**
     * The stamp is taken **when the engine is built**, not when a save starts.
     *
     * The window the stale guard covers is the editing session — from the decode the
     * canvas is drawing to the write. Reading it at save time would compare the file
     * to itself and catch nothing.
     *
     * A `var`, and re-read after every successful write, because a save now lands on
     * the file the editor is still holding open: without this the second `Done` of a
     * sitting would report the user's own first one as somebody else's change.
     */
    private var stamp = SourceStamp.of(file)

    /**
     * The write, then the two things that follow it. Which write it is, is [writeFor]'s
     * — §5.1's buckets are a branch over a value this class only carries.
     */
    override suspend fun save(layers: EditLayers): EditSaveResult =
        withContext(Dispatchers.IO) {
            val result = writeFor(capability, file, layers, frameIndex, stamp, ::reEncode)
            if (result is EditSaveResult.Saved) {
                stamp = SourceStamp.of(file)
                evictCachesFor(file)
            }
            result
        }

    /**
     * Back to as shot (ME-107) — the only place that *removes* rather than writes, and
     * answered by the same [EditSaveResult] as a save so the shell draws one banner.
     * **Scoped since ME-176**, and `revertRecipe` holds what each scope takes.
     */
    override suspend fun revert(scope: EditScope, frameIndex: Int): EditSaveResult =
        withContext(Dispatchers.IO) {
            val result = revertRecipe(file, scope, frameIndex)
            if (result is EditSaveResult.Saved) {
                stamp = SourceStamp.of(file)
                evictCachesFor(file)
            }
            result
        }

    /** Bucket B, in `ImageEditWrites` since ME-302 — its whole reasoning is there. */
    private fun reEncode(document: EditDocument): EditSaveResult =
        reEncodeStill(file, frameIndex, sourceSize, stamp, ::recipe, document)
}
