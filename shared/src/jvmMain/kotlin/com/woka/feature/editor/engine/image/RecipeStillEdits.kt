package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.core.ui.image.StillEdits
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.save.renderAtNativeScale
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The editor's half of ME-133's seam: a file's recipe, folded over what a reader
 * decoded.
 *
 * **No second render path.** `EditRecipe.read` → [fold] → [renderAtNativeScale], all
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
     * **Folded against the bitmap it was handed, not against the file's native size.**
     *
     * `StillPreview` may hand over a picture already scaled down by its decoder, and
     * folding against native would upscale that to full resolution and hand back a
     * blurred 24-megapixel preview. Every operation that exists today — rotate and flip
     * — is scale-free, so folding at the size actually present is exactly right.
     *
     * The first tool that addresses pixels (a crop rect, a markup stroke) makes this
     * the place that has to scale the recipe to the bitmap. It is one line and it is
     * that tool's ticket, not a debt this one is leaving quietly.
     */
    override fun apply(file: File, frameIndex: Int, decoded: ImageBitmap): ImageBitmap {
        val document = EditRecipe.read(file) ?: return decoded
        if (document.isEmpty) return decoded

        return runCatching {
            val size = IntSize(decoded.width, decoded.height)
            renderAtNativeScale(decoded, size, fold(document, registry, size))
                .toComposeImageBitmap()
        }.getOrDefault(decoded)
    }
}
