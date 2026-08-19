package com.woka.feature.editor.save

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.viewer.image.stillDecoderFor
import java.io.File

/**
 * A document becoming pixels, for the one bucket that has nowhere to record it.
 *
 * **This is no longer the save.** ME-132 made `EditRecipe` the default and left this
 * path exactly as ME-104 built it, serving §5.1's bucket B: a format Skia can encode
 * but no carrier can annotate, which today is `webp` alone and will be nothing at all
 * once ME-108's RIFF carrier lands. It re-encodes **in place**; there is no copy, no
 * target and no name to resolve.
 *
 * **Stale check first, before anything expensive.** A source that changed on disk
 * while the editor was open means someone else's work is about to be thrown away, and
 * finding that out after a 667 ms decode helps nobody.
 *
 * **No failure path writes to the source.** The atomic move is the last step and the
 * only step that touches a real path; everything before it fails into a `Failed` and
 * leaves the filesystem exactly as it was found.
 *
 * The native bitmap is decoded here, used, and dropped. It is deliberately **not** put
 * in `ImageBitmapCache`: a 100 MP source is 384 MB against a 256 MB budget (ME-010),
 * so admitting one evicts every thumbnail the app holds and still does not fit.
 */
internal class ImageSaveJob(
    private val file: File,
    private val frameIndex: Int,
    private val sourceSize: IntSize,
    private val stamp: SourceStamp,
    private val recipeOf: (EditDocument) -> RasterRecipe
) {

    /**
     * An empty document is still `NothingToDo` here, and only here: bucket B has no
     * recipe to remove, so re-encoding it would spend a generation of quality to
     * produce the picture that is already on disk. The *recorded* branch answers the
     * other way — emptying a document that carries a recipe is a Revert (§5.2).
     */
    fun save(document: EditDocument): EditSaveResult {
        if (document.isEmpty) return EditSaveResult.NothingToDo

        if (!stamp.matches(file)) {
            return EditSaveResult.Failed(
                "${file.name} changed on disk since it was opened. Nothing was written."
            )
        }

        val format = formatFor(file)
            ?: return EditSaveResult.Failed(
                "${file.extension.uppercase()} files cannot be saved directly. Convert first."
            )

        val bytes = runCatching { encode(document, format) }.getOrElse { error ->
            return EditSaveResult.Failed(
                "${file.name} could not be rendered (${error.message ?: "unknown error"}). " +
                    "The original is untouched."
            )
        }

        return writeAtomically(file, bytes).fold(
            onSuccess = { EditSaveResult.Saved(file.absolutePath) },
            onFailure = {
                EditSaveResult.Failed(
                    "${file.name} could not be written. The original is untouched."
                )
            }
        )
    }

    private fun encode(document: EditDocument, format: SaveFormat): ByteArray {
        // Native, once, and released when this returns.
        val native = stillDecoderFor(file).frame(file, frameIndex)
        val rendered = renderAtNativeScale(native, sourceSize, recipeOf(document))
        val encoded = rendered.use {
            it.encodeToData(format.skia, format.quality)?.bytes
                ?: error("the ${format.name} encoder produced nothing")
        }
        return if (format.carriesExif) spliceExif(encoded, file) else encoded
    }
}
