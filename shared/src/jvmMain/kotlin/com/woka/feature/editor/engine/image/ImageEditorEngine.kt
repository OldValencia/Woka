package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.MediaKind
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.SaveTarget
import com.woka.feature.viewer.image.stillDecoderFor
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
    private val registry: EditorToolRegistry
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

    internal fun recipe(document: EditDocument): RasterRecipe =
        fold(document, registry, sourceSize)

    @Composable
    override fun Surface(document: EditDocument, modifier: Modifier) {
        TODO("ME-103")
    }

    override suspend fun save(document: EditDocument, target: SaveTarget): EditSaveResult {
        TODO("ME-104")
    }
}
