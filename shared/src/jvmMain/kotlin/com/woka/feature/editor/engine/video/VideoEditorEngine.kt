package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The moving-picture half of the seam: folds a document into a [RasterRecipe] and draws
 * the player's current frame through it (ME-201).
 *
 * **Nothing here is a second implementation of anything.** The fold is the image
 * engine's `fold`, the draw is its `RasterSurface`, and every stage is expressed in
 * source pixels — so the five reused tools arrive by wiring rather than by porting
 * (§3.1). What this class adds is a `MediaKind`, a size, and a player.
 *
 * **It saves since ME-288**, into the container's own `uuid` box and without rewriting a
 * byte of the media data — the same road a `heic` takes, reached by two extensions in
 * `carrierFor` and made affordable on a 4 GB file by ME-280's in-place write.
 *
 * [recipe] is `internal` and absent from [EditorEngine] for the same reason the image
 * engine's is (§3.3): the moment the shell can name a raster type, every file that
 * touches one becomes a video ticket.
 */
internal class VideoEditorEngine(
    private val file: File,
    private val registry: EditorToolRegistry,
    /** Passed in, never re-derived: the factory has already asked. */
    override val capability: EditCapability
) : EditorEngine {

    override val kind = MediaKind.Video

    /**
     * The video track's **coded** size, read once.
     *
     * Never the arriving bitmap's: `RenderScale` re-opens the grabber at about the
     * surface's size, so one session can present 1920×1080 and 960×540 for the same
     * file. Operations are addressed in source pixels and `RasterSurface` stretches
     * whatever bitmap turned up — which is exactly what makes a varying decode size
     * harmless, and exactly what measuring the bitmap would break.
     */
    override val sourceSize: IntSize by lazy { codedFrameSize(file) }

    /** Owned here so `Surface` and `Thumbnail` share one decoder — see the holder. */
    private val playback = VideoPlayerHolder()

    internal fun recipe(document: EditDocument): RasterRecipe =
        fold(document, registry, sourceSize)

    /** The frame the recipe produces — a crop's dimensions, not the source's. */
    override fun drawnSize(document: EditDocument): IntSize = recipe(document).outputSize

    /** Where the recipe puts a source pixel, which is what an overlay has to follow. */
    override fun previewMatrix(document: EditDocument): Matrix = recipe(document).matrix

    override fun togglePlayback() {
        playback.state?.let { it.isPlaying = !it.isPlaying }
    }

    /**
     * The stream's colour, and **not the frame's** (ME-205). `streamHdrInfo` is written
     * once per opened track and is stable by contract, which is what a memoised recipe
     * needs; `currentFrameHdrInfo` is rewritten every frame, and keying a fold on it
     * would re-fold sixty times a second.
     */
    private val streamColour: HdrInfo? @Composable get() = playback.state?.streamHdrInfo

    /**
     * **Remembered on the document, not rebuilt per frame.** The frame is the only thing
     * that moves 60 times a second; re-folding a document that has not changed is the
     * one avoidable cost on this path.
     */
    @Composable
    override fun Surface(document: EditDocument, modifier: Modifier) {
        val colour = streamColour
        val recipe = remember(document, sourceSize, colour) {
            recipe(document).copy(source = hdrStageFor(colour))
        }
        // **The document says when a drag is in flight** and the canvas only needs the
        // answer: `pending` is set by `preview` and cleared by `commit`, so one boolean
        // covers a slider and a canvas tool alike (ME-207).
        VideoEditorCanvas(file, playback, sourceSize, recipe, document.pending != null, modifier)
    }

    /**
     * The band the shell makes room for (ME-206). It reads the same holder the canvas
     * fills, so there is one decoder behind the picture, the strip and every thumbnail.
     */
    @Composable
    override fun Timeline(document: EditDocument, modifier: Modifier) =
        VideoEditorBand(playback, modifier)

    @Composable
    override fun Thumbnail(document: EditDocument, modifier: Modifier) {
        val colour = streamColour
        val recipe = remember(document, sourceSize, colour) {
            recipe(document).copy(source = hdrStageFor(colour))
        }
        VideoEditorThumbnail(playback, sourceSize, recipe, modifier)
    }

    /** What the file carries, so a second sitting continues the first (ME-288). */
    override fun storedDocument(): EditDocument = storedVideoDocument(file)

    /** What a write does to the file, and the stamp it carries — see `VideoEditorWrites`. */
    private val writes = VideoEditorWrites(file, capability)

    override suspend fun save(layers: EditLayers): EditSaveResult =
        withContext(Dispatchers.IO) { writes.save(layers) }

    /**
     * **Both parameters are ignored, and the interface licenses it**: a video timeline has
     * no frame to key on, so the scope switch never appears for one.
     *
     * The `Done ▾` menu this enables, and the reason it prints on a container with no
     * carrier, are ME-286's. This is the red button telling the truth.
     */
    override suspend fun revert(scope: EditScope, frameIndex: Int): EditSaveResult =
        withContext(Dispatchers.IO) { writes.revert() }
}
