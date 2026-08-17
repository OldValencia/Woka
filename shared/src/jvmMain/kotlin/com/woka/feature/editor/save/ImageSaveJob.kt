package com.woka.feature.editor.save

import androidx.compose.ui.unit.IntSize
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.SaveTarget
import com.woka.feature.editor.core.copyName
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.viewer.image.stillDecoderFor
import java.io.File

/**
 * A document becoming a file, in one order that is written down once.
 *
 * **Stale check first, before anything expensive.** A source that changed on disk
 * while the editor was open means someone else's work is about to be thrown away, and
 * finding that out after a 667 ms decode helps nobody. It matters most for
 * `Overwrite`, where the loss is unrecoverable, but a copy rendered from a stale
 * decode is wrong too — just less expensively.
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

    fun save(document: EditDocument, target: SaveTarget): EditSaveResult {
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

        val destination = when (target) {
            is SaveTarget.Overwrite -> file
            is SaveTarget.Copy -> File(file.parentFile, target.name)
        }

        val bytes = runCatching { encode(document, format) }.getOrElse { error ->
            return EditSaveResult.Failed(
                "${file.name} could not be rendered (${error.message ?: "unknown error"}). " +
                    "The original is untouched."
            )
        }

        return writeAtomically(destination, bytes).fold(
            onSuccess = {
                if (target is SaveTarget.Overwrite) invalidate()
                EditSaveResult.Saved(destination.absolutePath)
            },
            onFailure = {
                EditSaveResult.Failed(
                    "${destination.name} could not be written. The original is untouched."
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

    /**
     * The exact invalidation list ME-010 measured, and only on overwrite.
     *
     * A copy is a path nothing has ever cached. The `.thumbnails` poster is keyed on
     * length plus mtime, so it orphans rather than going stale. That leaves these two,
     * both keyed on the bare path, both of which would otherwise keep showing the
     * pre-edit picture until something unrelated evicted them.
     */
    private fun invalidate() {
        val path = file.absolutePath
        ImageBitmapCache.remove(ImageBitmapCache.key(path, frameIndex))
        ImageBitmapCache.remove(ImageBitmapCache.previewKey(path, frameIndex))
    }
}

/**
 * The name a copy takes, resolved against what is already in the directory.
 *
 * The **source's** extension, not the format's. Nothing here converts: a `.jpeg`
 * re-encodes as JPEG and stays a `.jpeg` rather than silently becoming `.jpg`. ME-108
 * is where an extension changes, and it changes it because the user agreed to it.
 */
internal fun copyNameFor(file: File): String {
    val directory = file.parentFile
    return copyName(file.nameWithoutExtension, file.extension) { candidate ->
        File(directory, candidate).exists()
    }
}
