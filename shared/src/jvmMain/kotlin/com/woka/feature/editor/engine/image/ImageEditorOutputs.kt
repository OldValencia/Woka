package com.woka.feature.editor.engine.image

import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorExport
import com.woka.feature.editor.save.SourceStamp
import com.woka.feature.editor.save.evictCachesFor
import com.woka.feature.editor.save.renderStill
import com.woka.feature.editor.tools.resize.sizedFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

internal val RenderPhoto = EditorExport(
    id = "render-photo",
    title = "Render photo…",
    summary = "A new file beside this one. Opens edited anywhere."
)

internal abstract class ImageEditorOutputs(
    private val file: File,
    private val frameIndex: Int,
    override val capability: EditCapability
) : EditorEngine {
    internal abstract fun recipe(document: EditDocument): RasterRecipe

    override val exports = listOf(RenderPhoto)

    override suspend fun export(export: EditorExport, layers: EditLayers, progress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            try {
                require(export.id == RenderPhoto.id)
                check(stamp.matches(file)) { "${file.name} changed since it was opened" }
                // The copy's own size, not what `Done` saved (RD-401).
                val document = EditDocument(layers.base.ops + layers.frames[frameIndex]?.ops.orEmpty()).sizedFor(export.size)
                progress(0f)
                coroutineContext.ensureActive()
                val target = renderStill(file, frameIndex, sourceSize, recipe(document)) {
                    coroutineContext.ensureActive()
                }
                progress(1f)
                EditSaveResult.Saved(target.absolutePath)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                EditSaveResult.Failed("The photo could not be rendered: ${failure.message}. The original is untouched.")
            }
        }

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
