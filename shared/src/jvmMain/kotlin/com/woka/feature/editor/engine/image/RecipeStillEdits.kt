package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.ui.image.StillEdits
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.save.renderAtNativeScale
import com.woka.feature.viewer.image.stillDecoderFor
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The editor's half of ME-133's seam: a file's recipe, folded over what a reader
 * decoded.
 *
 * **No second render path.** `EditRecipe.documentIn` → [fold] → [renderAtNativeScale], all
 * three exactly as the save and the canvas use them. That is what keeps "what you saw
 * is what the file shows" a property of the design rather than two implementations
 * somebody has to keep in step.
 *
 * **Never throws.** A recipe written by a later build, a tool that has since been
 * removed, a surface that cannot be allocated — every one of them answers with the
 * unedited picture. Refusing to draw a photograph is a far larger harm than drawing it
 * without its edit, which is the line `EditRecipe` and `ExifSplice` already hold.
 */
internal class RecipeStillEdits(
    private val registry: EditorToolRegistry
) : StillEdits {

    override suspend fun carries(file: File): Boolean =
        withContext(ImageBitmapCache.ioDispatcher) {
            runCatching { EditRecipe.carries(file) }.getOrDefault(false)
        }

    /**
     * The payload's keys, and nothing derived from them (ME-157).
     *
     * **Never throws**, exactly as [carries] does not: a truncated or malformed
     * payload must not stop a frame strip from drawing.
     */
    override suspend fun editedFrames(file: File): Set<Int> =
        withContext(ImageBitmapCache.ioDispatcher) {
            runCatching { EditRecipe.read(file)?.frames?.keys.orEmpty() }
                .getOrDefault(emptySet())
        }

    /**
     * **Folded against the file's native size, then scaled to the bitmap** (ME-109).
     *
     * It used to fold against whatever bitmap arrived, and that was right for exactly
     * as long as every operation was scale-free: a rotation of a 400 px cover and of a
     * 6000 px original are the same matrix. A crop is not. Its rect is in **source**
     * pixels, so folded against a grid cover it would cut a region twenty times too
     * large and land nowhere near the picture the editor showed.
     *
     * So the fold happens where the operations are addressed — native — and
     * [scaledBy] moves the finished recipe into the bitmap's coordinates. A reader
     * handed a native bitmap scales by 1.0 and comes back bit-for-bit what it was.
     *
     * The native size is read from the decoder, which answers without decoding
     * anything. When it cannot answer, the bitmap's own size stands in: that is the
     * old behaviour, still correct for the scale-free tools and no worse than
     * refusing to draw.
     *
     * **[frameIndex] is finally read** (ME-126). It has been a parameter since
     * ME-133 and thrown away ever since, because one recipe covered every frame of a
     * file; the payload is keyed now, so this is where a burst's frame 3 gets its
     * own edit and frames 0, 1, 2 and 4 get the file's.
     */
    override fun apply(file: File, frameIndex: Int, decoded: ImageBitmap): ImageBitmap {
        val document = EditRecipe.documentIn(file, frameIndex) ?: return decoded
        if (document.isEmpty) return decoded

        return runCatching {
            val drawn = IntSize(decoded.width, decoded.height)
            val native = nativeSizeOf(file, frameIndex) ?: drawn
            val factor = if (native.width > 0) drawn.width.toFloat() / native.width else 1f
            val recipe = fold(document, registry, native).scaledBy(factor)
            renderAtNativeScale(decoded, drawn, recipe).toComposeImageBitmap()
        }.getOrDefault(decoded)
    }

    /** Null rather than throwing: a decoder that cannot measure must not stop a draw. */
    private fun nativeSizeOf(file: File, frameIndex: Int): IntSize? =
        runCatching { stillDecoderFor(file).nativeSize(file, frameIndex) }
            .getOrNull()
            ?.takeIf { it.width > 0 && it.height > 0 }
}
