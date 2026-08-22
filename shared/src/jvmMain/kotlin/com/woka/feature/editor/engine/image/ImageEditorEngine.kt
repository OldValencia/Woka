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
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.save.ImageSaveJob
import com.woka.feature.editor.save.SourceStamp
import com.woka.feature.editor.save.evictCachesFor
import com.woka.feature.viewer.image.stillDecoderFor
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

    /**
     * Read lazily, and from the **decoder** rather than from any bitmap: the
     * viewer may hold a picture capped below native while every zoom percentage
     * is defined against native 1:1 pixels, and `StillDecoder.nativeSize` answers
     * without decoding. Lazy because the factory decides editability from the
     * format alone and must not touch the disk to grey out a menu item.
     */
    override val sourceSize: IntSize by lazy {
        stillDecoderFor(file).nativeSize(file, frameIndex)
    }

    /**
     * What the file carries, so a second sitting continues the first (ME-134).
     *
     * The canvas is corrected by seeding the session and **not** by folding this into
     * the proxy: `EditorProxy` reads raw pixels by design (ME-133) and the session
     * folds the document over them, so applying it here as well would draw two
     * rotations for one.
     */
    override fun storedDocument(): EditDocument = EditRecipe.read(file) ?: EditDocument()

    internal fun recipe(document: EditDocument): RasterRecipe =
        fold(document, registry, sourceSize)

    /** The frame the recipe produces — a crop's dimensions, not the original's. */
    override fun drawnSize(document: EditDocument): IntSize = recipe(document).outputSize

    /** Where the recipe puts a source pixel, which is what an overlay has to follow. */
    override fun previewMatrix(document: EditDocument): Matrix = recipe(document).matrix

    /** Whether the canvas is running, when the file is something that runs. */
    private val playback = EditorPlayback()

    override fun togglePlayback() = playback.toggle()

    /**
     * The proxy is remembered on the file, **not on the document**: editing changes the
     * recipe, never the pixels it is applied to. An animation is the exception, and
     * `rememberEditorFrame` is where it lives.
     */
    @Composable
    override fun Surface(document: EditDocument, modifier: Modifier) =
        ImageCanvas(file, frameIndex, playback, sourceSize, recipe(document), modifier)

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
     * §5.1's three buckets, and nothing else decides.
     *
     * [EditCapability.Unavailable] is unreachable through the UI — the factory builds
     * no engine for it — and is answered honestly rather than by an `error()`, because
     * a save that throws is the one failure mode §4.5's banner cannot describe.
     */
    override suspend fun save(document: EditDocument): EditSaveResult =
        withContext(Dispatchers.IO) {
            val result = when (capability) {
                EditCapability.Recorded -> recordRecipe(file, document, stamp)
                EditCapability.Destructive -> reEncode(document)
                EditCapability.Unavailable -> EditSaveResult.Failed(
                    "${file.extension.uppercase()} files cannot be edited."
                )
            }
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

    /** Bucket B: no slot to record in, so the picture itself is re-encoded in place. */
    private fun reEncode(document: EditDocument): EditSaveResult =
        ImageSaveJob(
            file = file,
            frameIndex = frameIndex,
            sourceSize = sourceSize,
            stamp = stamp,
            recipeOf = ::recipe
        ).save(document)
}
