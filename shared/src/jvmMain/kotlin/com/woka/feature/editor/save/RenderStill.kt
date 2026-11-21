package com.woka.feature.editor.save

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.video.render.renderTargetBeside
import org.jetbrains.skia.Image
import java.io.File
import java.nio.file.Files

/** New pixels with no embedded recipe, so another reader never applies the edit twice. */
internal fun renderStill(
    source: File,
    frameIndex: Int,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    destination: File? = null,
    checkActive: () -> Unit = {}
): File {
    val stamp = SourceStamp.of(source)
    val (extension, bytes) = withBakedStill(source, frameIndex, sourceSize, recipe) { image ->
        val format = outputFormat(source, image)
        (format?.extension ?: "avif") to encodeStill(image, source, format)
    }
    checkActive()
    check(stamp.matches(source)) { "${source.name} changed while it was being rendered" }
    val target = destination?.let { File(it, "${source.nameWithoutExtension}.$extension") }
        ?: renderTargetBeside(source, extension)
    // Reserve the name exclusively before the atomic replacement; racing renders cannot overwrite.
    Files.createFile(target.toPath())
    try {
        writeAtomically(target, bytes).getOrThrow()
        checkActive()
        return target
    } catch (failure: Throwable) {
        target.delete()
        throw failure
    }
}

private fun outputFormat(source: File, image: Image): SaveFormat? {
    formatFor(source)?.let { return it }
    if (source.extension.equals("avif", ignoreCase = true)) return null
    return if (image.isOpaqueInPlace()) SaveFormat.JPEG else SaveFormat.PNG
}

/**
 * Whether any pixel is transparent, asked of Skia's own buffer: copying the picture onto the
 * heap to look (as this first did) costs hundreds of MB on a 100-megapixel RAW, against a
 * native image's 1.5 GB heap. Pixels that cannot be read in place answer "not opaque" — PNG
 * keeps an alpha that JPEG would drop.
 */
private fun Image.isOpaqueInPlace(): Boolean = peekPixels()?.use { it.computeIsOpaque() } ?: false
