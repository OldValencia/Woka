package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorFrames
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.SourceStats
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.save.ImageSaveJob
import com.woka.feature.editor.save.SourceStamp
import com.woka.feature.editor.save.evictCachesFor
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

    /**
     * Lazy for the same reason [sourceSize] is: a menu item is greyed out from the
     * format alone, and neither the frame count nor the stored payload may be read
     * to decide it.
     */
    override val frames: EditorFrames by lazy { editorFramesOf(file, frameIndex) }

    override fun storedDocument(): EditDocument = storedDocumentIn(file, frameIndex)

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

    /** One option of a filter strip, from the **same proxy** — see `ImageCanvas`. */
    @Composable
    override fun Thumbnail(document: EditDocument, modifier: Modifier) =
        ImageCanvas(file, frameIndex, playback, sourceSize, recipe(document), modifier)

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
     * The write, then the two things that follow every successful one. Which write
     * it is, is [writeFor]'s — §5.1's buckets are a branch over a value this class
     * only carries.
     */
    override suspend fun save(document: EditDocument, scope: EditScope): EditSaveResult =
        withContext(Dispatchers.IO) {
            val result =
                writeFor(capability, file, document, scope, frameIndex, stamp, ::reEncode)
            if (result is EditSaveResult.Saved) {
                stamp = SourceStamp.of(file)
                evictCachesFor(file)
            }
            result
        }

    /**
     * Back to as shot, in one action (ME-107) — and the only place that *removes*
     * rather than writes. Answered by the same [EditSaveResult] as a save, so the
     * shell has one outcome type and one banner to draw.
     */
    override suspend fun revert(): EditSaveResult = withContext(Dispatchers.IO) {
        val result = revertRecipe(file)
        if (result is EditSaveResult.Saved) {
            stamp = SourceStamp.of(file)
            evictCachesFor(file)
        }
        result
    }

    /**
     * Bucket B: no slot to record in, so the picture itself is re-encoded in place.
     *
     * **It takes no [EditScope] and could not use one** (ME-126): there is no payload
     * to key, the pixels themselves are rewritten, and what a frame set would mean
     * under a re-encode is a question nothing has asked — bucket B is empty today
     * (§5.1).
     */
    private fun reEncode(document: EditDocument): EditSaveResult =
        ImageSaveJob(
            file = file,
            frameIndex = frameIndex,
            sourceSize = sourceSize,
            stamp = stamp,
            recipeOf = ::recipe
        ).save(document)
}
