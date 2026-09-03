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
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.fold
import org.bytedeco.javacv.FFmpegFrameGrabber
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
 * **It cannot save, and says so** rather than failing quietly: [capability] is
 * `Preview` for the length of this phase and `save/`'s first ticket deletes both it and
 * the sentence below.
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
     * **Remembered on the document, not rebuilt per frame.** The frame is the only thing
     * that moves 60 times a second; re-folding a document that has not changed is the
     * one avoidable cost on this path.
     */
    @Composable
    override fun Surface(document: EditDocument, modifier: Modifier) {
        val recipe = remember(document, sourceSize) { recipe(document) }
        // **The document says when a drag is in flight** and the canvas only needs the
        // answer: `pending` is set by `preview` and cleared by `commit`, so one boolean
        // covers a slider and a canvas tool alike (ME-207).
        VideoEditorCanvas(file, playback, sourceSize, recipe, document.pending != null, modifier)
    }

    @Composable
    override fun Thumbnail(document: EditDocument, modifier: Modifier) {
        val recipe = remember(document, sourceSize) { recipe(document) }
        VideoEditorThumbnail(playback, sourceSize, recipe, modifier)
    }

    /**
     * **There is nowhere to write yet**, and the sentence is the one the banner prints
     * verbatim (§4.5). `revert` answers `NothingToDo` from the interface's own default,
     * because a file that carries nothing has nothing to remove.
     */
    override suspend fun save(layers: EditLayers): EditSaveResult =
        EditSaveResult.Failed("Video edits cannot be saved yet.")
}

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
private fun codedFrameSize(file: File): IntSize = runCatching {
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
